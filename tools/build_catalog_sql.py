#!/usr/bin/env python3
# =====================================================================
# build_catalog_sql.py : 약 카탈로그 원본 → MySQL 초기화 SQL(gzip) 변환 도구
#
# [입력]
#  1) catalog.jsonl : 식약처 낱알식별·허가정보를 약 1개 = 1줄(JSON)로 모은 파일 (약 950MB, 46,292건)
#  2) 심평원 DUR "병용금기 품목리스트" CSV (CP949 인코딩, 약 87만 행)
#  3) (선택) 심평원 약가마스터 의약품표준코드 CSV (CP949 인코딩)
#     → "제품코드(개정후)" 를 보험코드로 추가. 보험코드가 바뀐 약도 처방내역의 새 코드로 찾을 수 있게 한다.
#
# [출력]
#  db/initdb/03_insert_pill_catalog.sql.gz
#   → mysql 공식 이미지가 최초 실행 때 자동으로 압축을 풀어 실행한다.
#
# [만드는 테이블 데이터]  (테이블 구조는 01_create_schemas_tables.sql)
#  alyak_pill.pill_catalog      : 약 1개 = 1행. 웹 화면에 필요한 컬럼만 추린다
#  alyak_pill.pill_catalog_code : 보험코드(EDI) → 약. 처방내역의 약품코드로 약을 찾을 때 사용
#  alyak_pill.pill_catalog_dur  : 약별 DUR 주의 정보 (임부금기·연령금기·노인주의·병용금기, 약 상세 화면용)
#  alyak_pill.pill_catalog_ingredient : 약의 원료 성분 (복합제는 성분마다 1행, "같은 성분 중복 복용" 검사용)
#  alyak_pill.pill_ingredient   : 약 → 성분코드 (병용금기 검사용. 보험코드로 찾은 성분 + 표준코드의 일반명코드)
#  alyak_pill.dur_ingredient    : 보험코드 → 성분코드 (카탈로그와 연결되지 않은 처방약의 병용금기 검사용)
#  alyak_pill.dur_combination   : 함께 먹으면 안 되는 성분 쌍 (제품 쌍 87만 행 → 성분·투여경로 쌍 약 3천 8백 행)
#
# [성분코드(9자리)와 병용금기 키(5자리)]
#  성분코드 = 성분 4자리 + 함량 2자리 + 투여경로 1자리(A 내복, B 주사, C 외용 …) + 제형 2자리  예) 111501ATB
#  병용금기는 보통 "성분" 단위로 정해지므로, 함량·제형을 빼고 성분 4자리 + 투여경로 1자리(예: 1115A)로 비교한다.
#  → 목록에 없는 함량·제형의 약도 같은 성분이면 검사되고, 먹는 약과 바르는 약은 구분된다.
#
# [사용법] (저장소 최상위 폴더에서, 파이썬 표준 라이브러리만 사용)
#  python3 tools/build_catalog_sql.py \
#      --catalog ~/alyak-data/raw/catalog.jsonl \
#      --dur-combination "~/alyak-data/raw/dur/의약품안전사용서비스(DUR)_병용금기 품목리스트 2026.6.csv" \
#      --standard-code ~/alyak-data/raw/standard-code.csv \
#      --out db/initdb/03_insert_pill_catalog.sql.gz
# =====================================================================
import argparse
import csv
import gzip
import io
import json
import os
import re
from decimal import Decimal, InvalidOperation

# 한 번의 INSERT 문에 넣을 행 수 (여러 행을 한 문장으로 넣으면 초기화가 훨씬 빠르다)
ROWS_PER_INSERT = 500


# ---------------------------------------------------------------------
# 1. 카탈로그 정리 규칙
# ---------------------------------------------------------------------

# --- 약품명 정규화 ---
#  예: "타이레놀정500밀리그람(아세트아미노펜)" → "타이레놀정500mg"
#  출처마다 표기가 달라서(괄호 안 성분명, 띄어쓰기, 단위) 이름 검색은 이 값끼리 비교한다.
INNERMOST_BRACKETS = re.compile(r"\([^()]*\)|\[[^\[\]]*]|\{[^{}]*}|<[^<>]*>")
UNCLOSED_BRACKET = re.compile(r"[(\[{<].*$", re.DOTALL)
STRAY_CLOSING_BRACKET = re.compile(r"[)\]}>]")
WHITESPACE = re.compile(r"\s+")
EXPORT_NAME = re.compile(r"(?<=.)수출명.*$", re.DOTALL)  # 이름이 비지 않게 맨 앞의 수출명은 남긴다


def normalize_name(name):
    if not name:
        return ""
    current = name
    while True:  # 겹친 괄호 "타치온정(글루타티온(환원형))" 도 안쪽부터 반복해서 지운다
        previous = current
        current = INNERMOST_BRACKETS.sub("", previous)
        if current == previous:
            break
    current = UNCLOSED_BRACKET.sub("", current)
    current = STRAY_CLOSING_BRACKET.sub("", current)
    current = WHITESPACE.sub("", current)
    current = EXPORT_NAME.sub("", current)
    return (current.lower()
            .replace("밀리그램", "mg")
            .replace("밀리그람", "mg")
            .replace("마이크로그램", "μg")
            .replace("밀리리터", "ml"))


# --- 먹는 알약(정제·캡슐) 여부 ---
#  모양·색으로 찾는 "낱알 검색"은 알약만 대상으로 한다.
NOT_PILL_NAME = re.compile(r"구강(붕해|용해)필름|트로키|로젠지|껌")
NOT_PILL_FORM = re.compile(r"질정|질연질|질좌|흡입|분무|첩부|구강붕해필름|트로키|껌|부착정|^산제$")
PILL_NAME = re.compile(r"(정제?|캡슐|캅셀)([0-9]|$)")
NOT_PILL_CAPSULE = re.compile(r"공캡슐|캡슐[0-9]+호|흡입")
VAGINAL_TABLET_NAME = re.compile(r"질정|질좌")
VAGINAL_EFFICACY = re.compile(r"질염|질\s?칸디다|칸디다성|트리코모나스|질\s?감염|위축성\s?질|질\s?건조|자궁경부|질내")


def is_pill(item_name, form_code_name, efficacy):
    name = normalize_name(item_name)
    if NOT_PILL_NAME.search(name):
        return False
    if form_code_name and form_code_name.strip():  # 제형이 있으면 제형으로 판단
        return not NOT_PILL_FORM.search(form_code_name.strip())
    # 제형이 없으면 이름으로 판단 (질정은 효능까지 확인)
    vaginal = "질연질" in name or (
        VAGINAL_TABLET_NAME.search(name) is not None and efficacy is not None
        and VAGINAL_EFFICACY.search(efficacy) is not None)
    return bool(PILL_NAME.search(name)) and not NOT_PILL_CAPSULE.search(name) and not vaginal


# --- 판매 상태 : 판매되지 않는 약도 숨기지 않고 표시만 한다 ---
def sale_status(item_name, permit_status):
    if item_name and "수출용" in re.sub(r"\s", "", item_name):
        return "EXPORT_ONLY"
    if permit_status is None:
        return "UNKNOWN"
    return {
        "정상": "NORMAL",
        "유효기간만료": "EXPIRED",
        "취하": "WITHDRAWN",
        "취소": "CANCELLED",
        "행정(취소)": "CANCELLED",
        "폐업": "CANCELLED",
    }.get(permit_status, "UNKNOWN")


# ---------------------------------------------------------------------
# 2. 값 정리 도우미
# ---------------------------------------------------------------------
def text(obj, field):
    """dict 에서 문자열 값 꺼내기. 없거나 빈 문자열이면 None"""
    if not isinstance(obj, dict):
        return None
    value = obj.get(field)
    if value is None:
        return None
    value = str(value).strip()
    return value or None


def cut(value, max_length):
    """컬럼 길이(VARCHAR(n))를 넘으면 자른다"""
    return value if value is None or len(value) <= max_length else value[:max_length]


def dimension(value):
    """크기(mm) : 숫자가 아니거나 DECIMAL(6,2) 범위를 넘으면 None"""
    if value is None:
        return None
    try:
        number = Decimal(value)
    except InvalidOperation:
        return None
    return None if number < 0 or number > Decimal("9999.99") else number


INGREDIENT = re.compile(r"\[(M\d+)\]([^|]+)")

# 같은 성분인데 원료 형태만 다른 이름 (아세트아미노펜제피세립, 아세트아미노펜(미분화) ...) 의 꼬리 표기
INGREDIENT_FORM_SUFFIX = re.compile(r"(제피세립|제피과립|과립|세립|미분화|미립자|건조분말|코팅)$")


def ingredient_key(name):
    """비교용 성분명 : 괄호·공백을 지우고 원료 형태 꼬리 표기를 뗀다. 예) 아세트아미노펜 제피세립 → 아세트아미노펜"""
    key = re.sub(r"\([^)]*\)|\s+", "", name or "")
    previous = None
    while key != previous:
        previous = key
        key = INGREDIENT_FORM_SUFFIX.sub("", key)
    return key


def main_ingredient_rows(item_seq, main_ingredient):
    """주성분 "[M040353]아세트아미노펜|..." → (약, 원료 코드, 성분명, 비교용 성분명) 행들"""
    rows = {}
    for match in INGREDIENT.finditer(main_ingredient or ""):
        name = cut(match.group(2).strip(), 300)
        key = cut(ingredient_key(name), 300)
        if name and key:
            rows.setdefault(match.group(1), (item_seq, match.group(1), name, key))
    return list(rows.values())


def readable_ingredients(main_ingredient):
    """"[M040702]포도당|[M040426]염화나트륨" → "포도당, 염화나트륨" (화면 표시용)"""
    if not main_ingredient:
        return None
    names = [m.group(2).strip() for m in INGREDIENT.finditer(main_ingredient)]
    return ", ".join(dict.fromkeys(names)) if names else main_ingredient


def sql_value(value):
    """파이썬 값 → SQL 리터럴 (문자열은 MySQL 규칙대로 이스케이프)"""
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, (int, Decimal)):
        return str(value)
    escaped = (str(value)
               .replace("\\", "\\\\")
               .replace("'", "\\'")
               .replace("\n", "\\n")
               .replace("\r", "\\r")
               .replace("\x00", "\\0")
               .replace("\x1a", "\\Z"))
    return "'" + escaped + "'"


def write_inserts(out, table, columns, rows):
    """여러 행을 ROWS_PER_INSERT 개씩 묶어 INSERT 문으로 쓴다"""
    header = "INSERT INTO {} ({}) VALUES\n".format(table, ", ".join(columns))
    for start in range(0, len(rows), ROWS_PER_INSERT):
        chunk = rows[start:start + ROWS_PER_INSERT]
        values = ",\n".join("(" + ", ".join(sql_value(v) for v in row) + ")" for row in chunk)
        out.write(header + values + ";\n")


# ---------------------------------------------------------------------
# 3. 변환
# ---------------------------------------------------------------------
def read_catalog(path):
    """catalog.jsonl → pill_catalog 행, pill_catalog_code 행"""
    catalog_rows, code_rows, dur_rows, main_rows = [], [], [], []
    with open(path, encoding="utf-8") as file:
        for line in file:
            if not line.strip():
                continue
            pill = json.loads(line)
            item_seq = text(pill, "item_seq")
            if item_seq is None:
                continue
            permit = pill.get("permit") or {}
            ident = pill.get("identification") or {}
            description = pill.get("description") or {}
            item_name = cut(text(pill, "item_name"), 500)
            catalog_rows.append((
                item_seq,
                item_name,
                cut(normalize_name(item_name), 500),
                cut(text(pill, "entp_name"), 100),
                cut(text(permit, "etc_otc") or text(ident, "etc_otc"), 20),
                cut(text(ident, "class_name"), 100),
                readable_ingredients(text(permit, "main_ingredient")),
                cut(text(ident, "drug_shape"), 20),
                cut(text(ident, "color1"), 30),
                cut(text(ident, "color2"), 30),
                cut(text(ident, "print_front"), 100),
                cut(text(ident, "print_back"), 100),
                cut(text(ident, "line_front"), 10),
                cut(text(ident, "line_back"), 10),
                cut(text(ident, "form_code_name"), 50),
                cut(text(ident, "chart"), 500),
                dimension(text(ident, "leng_long")),
                dimension(text(ident, "leng_short")),
                dimension(text(ident, "thick")),
                cut(text(ident, "image_url") or text(description, "image_url"), 255),
                is_pill(item_name, text(ident, "form_code_name"), text(permit, "efficacy")),
                sale_status(item_name, text(permit, "status")),
                cut(text(permit, "atc_code"), 10),
            ))
            main_rows.extend(main_ingredient_rows(item_seq, text(permit, "main_ingredient")))
            dur_rows.extend(dur_rows_of(item_seq, pill.get("dur") or {}))
            # 보험코드(EDI) : 같은 약에 여러 개일 수 있고 중복이 있을 수 있다
            for code in pill.get("edi_codes") or []:
                code = edi_code(code)
                if code:
                    code_rows.append(("EDI", code, item_seq))
    return catalog_rows, code_rows, dur_rows, main_rows


def dur_rows_of(item_seq, dur):
    """
    약 1개의 DUR 정보 → pill_catalog_dur 행들
    원천 형식 : {"pregnancy": [{"금기등급", "상세정보"}], "age": [{"조건", "상세정보"}],
                 "elderly": ["상세 문장", ...], "combination": [{"상대성분", "상세정보"}]}
    (노인주의만 객체가 아니라 문자열 목록이다)
    """
    rows = []
    for item in dur.get("pregnancy") or []:
        rows.append((item_seq, "PREGNANCY", None, cut(text(item, "금기등급"), 10), None, cut(text(item, "상세정보"), 1000)))
    for item in dur.get("age") or []:
        rows.append((item_seq, "AGE", None, None, cut(text(item, "조건"), 20), cut(text(item, "상세정보"), 1000)))
    for item in dur.get("elderly") or []:
        detail = item.strip() if isinstance(item, str) else text(item, "상세정보")
        rows.append((item_seq, "ELDERLY", None, None, None, cut(detail or None, 1000)))
    for item in dur.get("combination") or []:
        rows.append((item_seq, "COMBINATION", cut(text(item, "상대성분"), 200), None, None,
                     cut(text(item, "상세정보"), 1000)))
    return rows


def edi_code(value):
    """
    보험코드 정리 : 9자리 숫자로 맞춘다. 형식이 이상하면 None.
    엑셀을 거친 원천 파일은 앞자리 0 이 빠진 8자리 코드가 섞여 있어서(예: 73400340) 앞에 0 을 붙인다.
    """
    code = str(value or "").strip()
    if code.isdigit() and len(code) == 8:
        code = "0" + code
    return code if code.isdigit() and len(code) == 9 else None


def read_standard_codes(path, known_item_seqs):
    """약가마스터 표준코드 CSV → (EDI, 제품코드(개정후), 품목기준코드) 행. 카탈로그에 있는 약만."""
    rows = []
    with open(path, encoding="cp949", newline="") as file:
        for row in csv.DictReader(file):
            item_seq = (row.get("품목기준코드") or "").strip()
            code = edi_code(row.get("제품코드(개정후)"))
            if item_seq in known_item_seqs and code:
                rows.append(("EDI", code, item_seq))
    return rows


def dur_key(ingredient_code):
    """성분코드 9자리 → 병용금기 비교 키 5자리 (성분 4자리 + 투여경로 1자리). 예) 111501ATB → 1115A"""
    return ingredient_code[:4] + ingredient_code[6:7]


def read_dur_combination(path):
    """
    병용금기 CSV(제품 쌍) → (보험코드-성분 매핑 행, 성분·경로 쌍 행, 보험코드→성분코드 dict)
    한 성분 쌍에 사유가 여러 개면 가장 많이 나온 사유를 쓴다.
    """
    ingredient_by_edi = {}
    pairs = {}
    names = {}
    # 심평원 CSV 는 CP949(한글 윈도우) 인코딩
    with open(path, encoding="cp949", newline="") as file:
        for row in csv.DictReader(file):
            for side in "AB":
                edi = row["제품코드" + side].strip()
                code = row["성분코드" + side].strip()
                if edi and code:
                    ingredient_by_edi[edi] = (code, cut(row["성분명" + side].strip(), 500))
            code_a, code_b = row["성분코드A"].strip(), row["성분코드B"].strip()
            if len(code_a) != 9 or len(code_b) != 9:
                continue
            key_a, key_b = dur_key(code_a), dur_key(code_b)
            if key_a == key_b:
                continue
            names.setdefault(key_a, cut(row["성분명A"].strip(), 300))
            names.setdefault(key_b, cut(row["성분명B"].strip(), 300))
            key = tuple(sorted((key_a, key_b)))  # (A,B) 와 (B,A) 를 같은 쌍으로
            pairs.setdefault(key, {})
            detail = row["상세정보"].strip()
            pairs[key][detail] = pairs[key].get(detail, 0) + 1
    edi_rows = [(edi, code, name) for edi, (code, name) in sorted(ingredient_by_edi.items())]
    pair_rows = [(a, b, names[a], names[b], cut(max(details, key=details.get), 1000) or None)
                 for (a, b), details in sorted(pairs.items())]
    return edi_rows, pair_rows, {edi: code for edi, (code, _) in ingredient_by_edi.items()}


def read_generic_codes(path, known_item_seqs):
    """약가마스터 표준코드 CSV → {품목기준코드: {일반명코드(성분코드 9자리), ...}}. 카탈로그에 있는 약만."""
    generic = {}
    with open(path, encoding="cp949", newline="") as file:
        for row in csv.DictReader(file):
            item_seq = (row.get("품목기준코드") or "").strip()
            code = (row.get("일반명코드(성분명코드)") or "").strip()
            if item_seq in known_item_seqs and len(code) == 9:
                generic.setdefault(item_seq, set()).add(code)
    return generic


def ingredient_rows_of(code_rows, edi_to_ingredient, generic_codes):
    """약 → 성분코드 행 : 약의 보험코드로 찾은 성분 + 표준코드의 일반명코드 (중복 제거)"""
    by_item = {}
    for code_type, code, item_seq in code_rows:
        if code_type == "EDI" and code in edi_to_ingredient:
            by_item.setdefault(item_seq, set()).add(edi_to_ingredient[code])
    for item_seq, codes in generic_codes.items():
        by_item.setdefault(item_seq, set()).update(codes)
    return [(item_seq, code) for item_seq in sorted(by_item) for code in sorted(by_item[item_seq])]


def main():
    parser = argparse.ArgumentParser(description="약 카탈로그 jsonl + DUR 병용금기 CSV → MySQL 초기화 SQL(gzip)")
    parser.add_argument("--catalog", required=True, help="catalog.jsonl 경로")
    parser.add_argument("--dur-combination", required=True, help="DUR 병용금기 품목리스트 CSV 경로")
    parser.add_argument("--standard-code", help="(선택) 약가마스터 의약품표준코드 CSV 경로")
    parser.add_argument("--out", required=True, help="출력 파일 (.sql.gz)")
    args = parser.parse_args()

    catalog_rows, code_rows, dur_rows, main_rows = read_catalog(os.path.expanduser(args.catalog))
    if args.standard_code:
        code_rows += read_standard_codes(os.path.expanduser(args.standard_code), {r[0] for r in catalog_rows})
    # (코드 종류, 코드, 약) 이 같은 행은 하나만 (테이블의 UNIQUE 키와 같은 기준)
    code_rows = list(dict.fromkeys(code_rows))
    edi_rows, pair_rows, edi_to_ingredient = read_dur_combination(os.path.expanduser(args.dur_combination))
    generic_codes = (read_generic_codes(os.path.expanduser(args.standard_code), {r[0] for r in catalog_rows})
                     if args.standard_code else {})
    ingredient_rows = ingredient_rows_of(code_rows, edi_to_ingredient, generic_codes)

    # mtime=0 : 같은 입력이면 항상 같은 파일이 나오게 (git 에서 불필요한 변경 방지)
    with gzip.GzipFile(args.out, "wb", compresslevel=9, mtime=0) as raw, \
            io.TextIOWrapper(raw, encoding="utf-8") as out:
        out.write("-- =====================================================================\n"
                  "-- 내약통한눈에 DB 초기화 스크립트 (3/3) : 약 카탈로그 + DUR 병용금기 데이터\n"
                  "--  tools/build_catalog_sql.py 가 자동 생성한 파일 (직접 수정하지 말 것)\n"
                  "-- =====================================================================\n"
                  "SET NAMES utf8mb4;\n"
                  "SET autocommit = 0;\n\n")
        write_inserts(out, "alyak_pill.pill_catalog",
                      ["item_seq", "item_name", "item_name_normalized", "entp_name", "etc_otc", "class_name",
                       "main_ingredient", "drug_shape", "color1", "color2", "print_front", "print_back",
                       "line_front", "line_back", "form_code_name", "chart", "leng_long", "leng_short", "thick",
                       "image_url", "is_pill", "sale_status", "atc_code"], catalog_rows)
        write_inserts(out, "alyak_pill.pill_catalog_code", ["code_type", "code", "item_seq"], code_rows)
        write_inserts(out, "alyak_pill.pill_catalog_dur",
                      ["item_seq", "dur_type", "partner_ingredient", "grade", "age_condition", "detail"], dur_rows)
        write_inserts(out, "alyak_pill.pill_catalog_ingredient",
                      ["item_seq", "ingredient_code", "ingredient_name", "ingredient_key"], main_rows)
        write_inserts(out, "alyak_pill.pill_ingredient", ["item_seq", "ingredient_code"], ingredient_rows)
        write_inserts(out, "alyak_pill.dur_ingredient", ["edi_code", "ingredient_code", "ingredient_name"], edi_rows)
        write_inserts(out, "alyak_pill.dur_combination",
                      ["ingredient_key_a", "ingredient_key_b", "ingredient_name_a", "ingredient_name_b", "detail"],
                      pair_rows)
        out.write("\nCOMMIT;\n")

    print("pill_catalog      {:>7,} 행 (알약 {:,})".format(len(catalog_rows), sum(1 for r in catalog_rows if r[20])))
    print("pill_catalog_code {:>7,} 행".format(len(code_rows)))
    print("pill_catalog_dur  {:>7,} 행".format(len(dur_rows)))
    print("pill_catalog_ingredient {:>7,} 행 (ATC 있는 약 {:,})".format(len(main_rows),
                                                                 sum(1 for r in catalog_rows if r[22])))
    print("pill_ingredient   {:>7,} 행 (약 {:,}개)".format(len(ingredient_rows), len({r[0] for r in ingredient_rows})))
    print("dur_ingredient    {:>7,} 행".format(len(edi_rows)))
    print("dur_combination   {:>7,} 행".format(len(pair_rows)))
    print("출력 : {} ({:.1f} MB)".format(args.out, os.path.getsize(args.out) / 1024 / 1024))


if __name__ == "__main__":
    main()
