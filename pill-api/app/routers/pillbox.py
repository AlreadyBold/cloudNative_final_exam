"""
내 약통 API (로그인 필요) : 사용자가 먹는 약과 복용 기간
  GET    /api/pill/pillbox       : 목록 (복용 상태 포함 : 복용 중 → 복용 예정 → 복용 완료 순)
  POST   /api/pill/pillbox       : 담기 (약 검색 결과 또는 처방내역에서, 복용 시작일·종료일 지정)
  POST   /api/pill/pillbox/prescriptions : 불러온 처방을 한 번에 담기 (복용 기간은 처방으로 계산)
  GET    /api/pill/pillbox/interactions  : 병용금기 검사 (함께 먹으면 안 되는 약 쌍)
  PATCH  /api/pill/pillbox/{id}  : 복용 기간·메모 수정
  DELETE /api/pill/pillbox/{id}  : 빼기

[복용 상태] 저장하지 않고 조회할 때마다 오늘 날짜로 계산한다.
  오늘 < 시작일           → UPCOMING (복용 예정)
  시작일 ≤ 오늘 ≤ 종료일   → TAKING   (복용 중)
  종료일 < 오늘           → DONE     (복용 완료)
  "오늘"은 DB 서버의 날짜(CURDATE(), 한국 시간 +09:00)를 써서 어느 컨테이너가 계산해도 같다.

[처방에서 담을 때의 복용 기간]
  처방 1건 안의 약은 모두 같은 기간 : 조제일 ~ 조제일 + (그 처방에서 가장 긴 투약일수 - 1)
  예) 2026-09-29 조제, 약이 5일·7일 → 모든 약 2026-09-29 ~ 2026-10-05 (7일)

회원 번호(user_id)는 요청 본문이 아니라 "세션"에서 가져온다. 그래서 다른 사람의 약통은 볼 수도, 고칠 수도 없다.
"""
import datetime
from typing import List, Literal, Optional

from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field, model_validator
from sqlalchemy import bindparam, text

from app.db import engine, rows_to_dicts
from app.errors import BusinessError, ErrorCode, ok
from app.routers.catalog import match_catalog
from app.session import LoginUser, login_required

router = APIRouter(prefix="/api/pill/pillbox", tags=["내 약통"])

# 한 번에 지정할 수 있는 최대 복용 기간 (입력 실수로 수십 년이 들어가는 것을 막는다)
MAX_PERIOD_DAYS = 3650

# 약통 항목 + 복용 상태 + 카탈로그 정보(사진, 모양). 카탈로그에 없는 약도 보여 주기 위해 LEFT JOIN
SELECT_ITEMS = """
    SELECT b.id, b.item_seq, b.drug_code, b.drug_name, b.start_date, b.end_date, b.memo, b.source, b.created_at,
           b.dispensed_date, b.prescriber, b.pharmacy, b.ingredients, b.one_dose, b.daily_doses,
           b.total_dosing_days, b.effect, b.data_source,
           CASE WHEN b.start_date > CURDATE() THEN 'UPCOMING'
                WHEN b.end_date < CURDATE()   THEN 'DONE'
                ELSE 'TAKING' END AS status,
           DATEDIFF(b.end_date, CURDATE()) AS days_left,
           p.image_url, p.entp_name, p.drug_shape, p.color1, p.etc_otc, p.class_name
    FROM pillbox_items b
    LEFT JOIN pill_catalog p ON p.item_seq = b.item_seq
"""


class Period(BaseModel):
    """복용 기간 검사 : 종료일이 시작일보다 빠르거나, 기간이 너무 길면 거절"""

    @model_validator(mode="after")
    def check_period(self):
        start, end = getattr(self, "start_date", None), getattr(self, "end_date", None)
        if start and end:
            if end < start:
                raise ValueError("복용 종료일은 시작일보다 빠를 수 없습니다.")
            if (end - start).days > MAX_PERIOD_DAYS:
                raise ValueError(f"복용 기간은 최대 {MAX_PERIOD_DAYS}일까지 정할 수 있습니다.")
        return self


class AddRequest(Period):
    """
    담기 요청. 약 정보(셋 중 하나) + 복용 기간.
      약 검색에서 담기  : {"item_seq": "200300406", "start_date": "2026-11-08", "end_date": "2026-11-14",
                          "source": "SEARCH"}
      처방에서 담기     : {"drug_code": "073400340", "drug_name": "리피토정10밀리그램", ..., "source": "PRESCRIPTION"}
    """
    item_seq: Optional[str] = Field(None, max_length=20)
    drug_code: Optional[str] = Field(None, max_length=20)
    drug_name: Optional[str] = Field(None, max_length=500)
    start_date: datetime.date
    end_date: datetime.date
    memo: Optional[str] = Field(None, max_length=200)
    source: Literal["SEARCH", "PRESCRIPTION"]


class UpdateRequest(Period):
    """수정 요청. 보낸 항목만 바꾼다. 예) {"start_date": "...", "end_date": "...", "memo": "아침 식후 1알"}"""
    start_date: Optional[datetime.date] = None
    end_date: Optional[datetime.date] = None
    memo: Optional[str] = Field(None, max_length=200)


def find_item(conn, user_id, item_id):
    row = conn.execute(text(SELECT_ITEMS + " WHERE b.id = :id AND b.user_id = :user_id"),
                       {"id": item_id, "user_id": user_id}).first()
    if row is None:
        raise BusinessError(ErrorCode.PILLBOX_ITEM_NOT_FOUND)
    return dict(row._mapping)


def is_duplicate(conn, user_id, item_seq, drug_code, drug_name, start_date):
    """
    같은 약을 같은 날부터 먹는 항목이 이미 있는지.
    같은 약이라도 기간이 다르면(예: 지난달 처방, 이번 달 처방) 따로 담을 수 있다.
    약이 같은지는 카탈로그 약 ID → 보험코드 → 약품명 순으로 비교한다.
    """
    return conn.execute(text("""
        SELECT 1 FROM pillbox_items
        WHERE user_id = :user_id AND start_date = :start_date
          AND (   (:item_seq IS NOT NULL AND item_seq = :item_seq)
               OR (:item_seq IS NULL AND :drug_code IS NOT NULL AND drug_code = :drug_code)
               OR (:item_seq IS NULL AND :drug_code IS NULL AND drug_name = :drug_name))
        LIMIT 1
    """), {"user_id": user_id, "start_date": start_date, "item_seq": item_seq,
           "drug_code": drug_code, "drug_name": drug_name}).first() is not None


@router.get("")
def list_items(user: LoginUser = Depends(login_required)):
    """내 약통 목록 : 복용 중 → 복용 예정 → 복용 완료, 같은 상태 안에서는 최근에 시작한 순"""
    with engine.connect() as conn:
        rows = rows_to_dicts(conn.execute(text(SELECT_ITEMS + """
            WHERE b.user_id = :user_id
            ORDER BY FIELD(status, 'TAKING', 'UPCOMING', 'DONE'), b.start_date DESC, b.id DESC
        """), {"user_id": user.user_id}))
    return ok(rows)


@router.post("", status_code=201)
def add_item(body: AddRequest, user: LoginUser = Depends(login_required)):
    """약통에 약 담기. 같은 약을 같은 시작일로 두 번 담으면 409."""
    if not (body.item_seq or body.drug_code or body.drug_name):
        raise BusinessError(ErrorCode.COMMON_INVALID_INPUT, "담을 약 정보가 없습니다.")

    # engine.begin() : 블록이 끝나면 커밋, 예외가 나면 롤백
    with engine.begin() as conn:
        item_seq = body.item_seq
        drug_name = body.drug_name
        if item_seq:
            # 검색에서 담은 경우 : 카탈로그에 있는 약인지 확인하고 이름은 카탈로그 이름을 쓴다
            catalog_name = conn.execute(text("SELECT item_name FROM pill_catalog WHERE item_seq = :s"),
                                        {"s": item_seq}).scalar()
            if catalog_name is None:
                raise BusinessError(ErrorCode.CATALOG_NOT_FOUND)
            drug_name = drug_name or catalog_name
        else:
            # 처방내역에서 담은 경우 : 보험코드 → 이름 순으로 카탈로그 약을 찾아 연결 (없으면 이름만 저장)
            item_seq = match_catalog(conn, body.drug_code, body.drug_name)
            if not drug_name:
                if item_seq is None:
                    raise BusinessError(ErrorCode.COMMON_INVALID_INPUT, "약 이름이 필요합니다.")
                drug_name = conn.execute(text("SELECT item_name FROM pill_catalog WHERE item_seq = :s"),
                                         {"s": item_seq}).scalar()

        if is_duplicate(conn, user.user_id, item_seq, body.drug_code, drug_name, body.start_date):
            raise BusinessError(ErrorCode.PILLBOX_DUPLICATE)

        result = conn.execute(text("""
            INSERT INTO pillbox_items (user_id, item_seq, drug_code, drug_name, start_date, end_date, memo, source)
            VALUES (:user_id, :item_seq, :drug_code, :drug_name, :start_date, :end_date, :memo, :source)
        """), {"user_id": user.user_id, "item_seq": item_seq, "drug_code": body.drug_code, "drug_name": drug_name,
               "start_date": body.start_date, "end_date": body.end_date,
               "memo": (body.memo or "").strip() or None, "source": body.source})
        return ok(find_item(conn, user.user_id, result.lastrowid))


class PrescriptionDrug(BaseModel):
    """처방 안의 약 1개 (user-api 응답의 drugs[i] 와 같은 필드 이름)"""
    drug_code: Optional[str] = Field(None, max_length=20)
    name: str = Field(..., min_length=1, max_length=500)
    ingredients: Optional[str] = Field(None, max_length=1000)
    one_dose: Optional[str] = Field(None, max_length=20)
    daily_doses: Optional[str] = Field(None, max_length=20)
    total_dosing_days: Optional[str] = Field(None, max_length=20)
    effect: Optional[str] = Field(None, max_length=200)


class Prescription(BaseModel):
    """처방 1건 (user-api 응답의 prescriptions[i] 와 같은 필드 이름)"""
    dispensed_date: Optional[datetime.date] = None
    pharmacy: Optional[str] = Field(None, max_length=100)
    prescriber: Optional[str] = Field(None, max_length=100)
    drugs: List[PrescriptionDrug] = Field(..., min_length=1, max_length=100)


class PrescriptionsRequest(BaseModel):
    """예) {"source": "CODEF", "prescriptions": [{...}, {...}]} — user-api 완료 응답에서 고른 처방 그대로"""
    source: Literal["CODEF", "MOCK"]
    prescriptions: List[Prescription] = Field(..., min_length=1, max_length=200)


def dosing_days(value):
    """총 투약일수 문자열 → 일수. 숫자가 아니거나 비어 있으면 1일로 본다"""
    try:
        return max(1, min(int(str(value).strip()), MAX_PERIOD_DAYS))
    except (TypeError, ValueError):
        return 1


def prescription_period(prescription):
    """처방 1건의 복용 기간 : 조제일(없으면 오늘)부터 가장 긴 투약일수만큼"""
    start = prescription.dispensed_date or datetime.date.today()
    days = max(dosing_days(d.total_dosing_days) for d in prescription.drugs)
    return start, start + datetime.timedelta(days=days - 1)


@router.post("/prescriptions", status_code=201)
def add_prescriptions(body: PrescriptionsRequest, user: LoginUser = Depends(login_required)):
    """
    불러온 처방을 약통에 한 번에 담는다. 같은 약을 같은 시작일로 이미 담았으면 건너뛴다.
    응답 예) {"saved_count": 6, "skipped_count": 0}
    """
    saved = skipped = 0
    with engine.begin() as conn:
        for prescription in body.prescriptions:
            start, end = prescription_period(prescription)
            for drug in prescription.drugs:
                # 보험코드 → 약품명 순으로 카탈로그 약을 찾아 연결 (못 찾으면 NULL, 처방 정보만 보관)
                item_seq = match_catalog(conn, drug.drug_code, drug.name)
                if is_duplicate(conn, user.user_id, item_seq, drug.drug_code, drug.name, start):
                    skipped += 1
                    continue
                conn.execute(text("""
                    INSERT INTO pillbox_items
                        (user_id, item_seq, drug_code, drug_name, start_date, end_date, source,
                         dispensed_date, prescriber, pharmacy, ingredients, one_dose, daily_doses,
                         total_dosing_days, effect, data_source)
                    VALUES (:user_id, :item_seq, :drug_code, :name, :start, :end, 'PRESCRIPTION',
                            :dispensed_date, :prescriber, :pharmacy, :ingredients, :one_dose, :daily_doses,
                            :total_dosing_days, :effect, :data_source)
                """), {**drug.model_dump(), "user_id": user.user_id, "item_seq": item_seq, "start": start, "end": end,
                       "dispensed_date": prescription.dispensed_date, "prescriber": prescription.prescriber,
                       "pharmacy": prescription.pharmacy, "data_source": body.source})
                saved += 1
    return ok({"saved_count": saved, "skipped_count": skipped})


def dur_key(ingredient_code):
    """성분코드 9자리 → 병용금기 비교 키 (성분 4자리 + 투여경로 1자리). 예) 111501ATB → 1115A"""
    return ingredient_code[:4] + ingredient_code[6:7]


@router.get("/interactions")
def interactions(user: LoginUser = Depends(login_required)):
    """
    병용금기 검사 : 아직 끝나지 않은 약(복용 중·복용 예정) 중 복용 기간이 겹치는 두 약이
    DUR 병용금기 성분 쌍에 해당하는지 본다. (끝난 약과는 함께 먹는 게 아니므로 검사하지 않는다)

    [약의 성분 찾기]
      카탈로그와 연결된 약 : pill_ingredient (보험코드로 찾은 성분 + 표준코드의 일반명코드)
      보험코드가 있는 약   : dur_ingredient (처방에만 있고 카탈로그와 연결되지 않은 약도 검사)
    [비교] 성분코드를 "성분 4자리 + 투여경로 1자리" 키로 바꿔 dur_combination 에서 찾는다.

    응답 예)
      {"pairs": [{"item_a": {...}, "item_b": {...}, "ingredient_a": "atorvastatin ...",
                  "ingredient_b": "itraconazole", "detail": "근육병증 ...",
                  "overlap_start": "2026-11-10", "overlap_end": "2026-11-16"}],
       "items": [{"id": 3, "check": "CONFLICT"}, ...]}   ← CONFLICT 병용금기 / CHECKED 확인됨 / UNKNOWN 성분 정보 없음
    """
    with engine.connect() as conn:
        items = rows_to_dicts(conn.execute(text("""
            SELECT id, item_seq, drug_code, drug_name, start_date, end_date
            FROM pillbox_items
            WHERE user_id = :user_id AND end_date >= CURDATE()
            ORDER BY start_date, id
        """), {"user_id": user.user_id}))

        # 약마다 성분·경로 키 모으기
        keys = {item["id"]: set() for item in items}
        seqs = sorted({i["item_seq"] for i in items if i["item_seq"]})
        if seqs:
            rows = conn.execute(text("SELECT item_seq, ingredient_code FROM pill_ingredient WHERE item_seq IN :seqs")
                                .bindparams(bindparam("seqs", expanding=True)), {"seqs": seqs}).all()
            by_seq = {}
            for seq, code in rows:
                by_seq.setdefault(seq, set()).add(dur_key(code))
            for item in items:
                keys[item["id"]] |= by_seq.get(item["item_seq"], set())
        codes = sorted({i["drug_code"] for i in items if i["drug_code"]})
        if codes:
            rows = conn.execute(text("SELECT edi_code, ingredient_code FROM dur_ingredient WHERE edi_code IN :codes")
                                .bindparams(bindparam("codes", expanding=True)), {"codes": codes}).all()
            by_code = {edi: dur_key(code) for edi, code in rows}
            for item in items:
                if item["drug_code"] in by_code:
                    keys[item["id"]].add(by_code[item["drug_code"]])

        # 내 약들의 키 사이에 있는 병용금기 쌍만 가져온다
        all_keys = sorted(set().union(*keys.values())) if keys else []
        combos = {}
        if all_keys:
            rows = rows_to_dicts(conn.execute(text("""
                SELECT ingredient_key_a, ingredient_key_b, ingredient_name_a, ingredient_name_b, detail
                FROM dur_combination
                WHERE ingredient_key_a IN :keys AND ingredient_key_b IN :keys
            """).bindparams(bindparam("keys", expanding=True)), {"keys": all_keys}))
            combos = {(r["ingredient_key_a"], r["ingredient_key_b"]): r for r in rows}

    # 기간이 겹치는 두 약마다 성분 키 쌍을 비교
    pairs, conflict_ids = [], set()
    for index, a in enumerate(items):
        for b in items[index + 1:]:
            if a["start_date"] > b["end_date"] or b["start_date"] > a["end_date"]:
                continue  # 복용 기간이 겹치지 않음
            found = set()
            for key_a in keys[a["id"]]:
                for key_b in keys[b["id"]]:
                    combo = combos.get(tuple(sorted((key_a, key_b))))
                    if combo is None or (combo["ingredient_key_a"], combo["ingredient_key_b"]) in found:
                        continue
                    found.add((combo["ingredient_key_a"], combo["ingredient_key_b"]))
                    a_first = combo["ingredient_key_a"] == key_a
                    pairs.append({
                        "item_a": {"id": a["id"], "drug_name": a["drug_name"]},
                        "item_b": {"id": b["id"], "drug_name": b["drug_name"]},
                        "ingredient_a": combo["ingredient_name_a"] if a_first else combo["ingredient_name_b"],
                        "ingredient_b": combo["ingredient_name_b"] if a_first else combo["ingredient_name_a"],
                        "detail": combo["detail"],
                        "overlap_start": max(a["start_date"], b["start_date"]),
                        "overlap_end": min(a["end_date"], b["end_date"]),
                    })
                    conflict_ids.update((a["id"], b["id"]))

    checks = [{"id": i["id"], "check": "CONFLICT" if i["id"] in conflict_ids
               else ("CHECKED" if keys[i["id"]] else "UNKNOWN")} for i in items]
    return ok({"pairs": pairs, "items": checks})


@router.patch("/{item_id}")
def update_item(item_id: int, body: UpdateRequest, user: LoginUser = Depends(login_required)):
    """복용 기간·메모 수정 (보낸 항목만)"""
    with engine.begin() as conn:
        current = find_item(conn, user.user_id, item_id)  # 내 약통의 항목인지 확인 (아니면 404)
        start = body.start_date or current["start_date"]
        end = body.end_date or current["end_date"]
        if end < start:
            raise BusinessError(ErrorCode.COMMON_INVALID_INPUT, "복용 종료일은 시작일보다 빠를 수 없습니다.")
        memo = current["memo"] if "memo" not in body.model_fields_set else ((body.memo or "").strip() or None)
        conn.execute(text("""
            UPDATE pillbox_items SET start_date = :start, end_date = :end, memo = :memo
            WHERE id = :id AND user_id = :user_id
        """), {"start": start, "end": end, "memo": memo, "id": item_id, "user_id": user.user_id})
        return ok(find_item(conn, user.user_id, item_id))


@router.delete("/{item_id}")
def delete_item(item_id: int, user: LoginUser = Depends(login_required)):
    """약통에서 빼기"""
    with engine.begin() as conn:
        deleted = conn.execute(text("DELETE FROM pillbox_items WHERE id = :id AND user_id = :user_id"),
                               {"id": item_id, "user_id": user.user_id}).rowcount
    if deleted == 0:
        raise BusinessError(ErrorCode.PILLBOX_ITEM_NOT_FOUND)
    return ok()
