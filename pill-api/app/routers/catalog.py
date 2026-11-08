"""
약 카탈로그 API (로그인 없이 사용 가능)
  GET  /api/pill/catalog/search?q=타이레놀&page=1&size=20 : 약 이름 검색
  GET  /api/pill/catalog/{item_seq}                      : 약 상세 (DUR 주의 정보 포함)
"""
from fastapi import APIRouter, Query
from sqlalchemy import text

from app.db import engine, rows_to_dicts
from app.errors import BusinessError, ErrorCode, ok
from app.names import escape_like, normalize_name

router = APIRouter(prefix="/api/pill/catalog", tags=["약 카탈로그"])

# 목록·요약에 공통으로 내보낼 컬럼
SUMMARY_COLUMNS = """
    p.item_seq, p.item_name, p.entp_name, p.etc_otc, p.class_name,
    p.drug_shape, p.color1, p.color2, p.form_code_name, p.image_url,
    p.is_pill, p.sale_status
"""


def match_catalog(conn, drug_code, drug_name):
    """
    처방내역의 약 → 카탈로그 약 ID(item_seq). 못 찾으면 None (처방에 온 이름·성분만으로 보관·표시).

    1) 보험코드로 찾기
       CODEF 가 주는 약품코드(9자리 보험코드)를 pill_catalog_code 에서 찾는다.
       한 코드에 약이 여러 개면 판매 상태가 정상인 약 → 품목기준코드 순으로 하나를 고른다.
       ※ code_type 조건을 꼭 넣어야 (code_type, code, item_seq) 인덱스를 탄다.
    2) 약품명으로 찾기 (보조, 코드로 못 찾은 약만)
       띄어쓰기·괄호를 없앤 이름이 같은 "정상 판매" 약이 딱 1개일 때만 연결한다.
       여러 개면 엉뚱한 약에 연결될 수 있어서 연결하지 않는다.
    """
    code = (drug_code or "").strip()
    if len(code) == 8 and code.isdigit():
        code = "0" + code  # 앞자리 0 이 빠진 코드 보정
    if code:
        item_seq = conn.execute(text("""
            SELECT c.item_seq FROM pill_catalog_code c
            JOIN pill_catalog p ON p.item_seq = c.item_seq
            WHERE c.code_type = 'EDI' AND c.code = :code
            ORDER BY (p.sale_status = 'NORMAL') DESC, p.item_seq
            LIMIT 1
        """), {"code": code}).scalar()
        if item_seq:
            return item_seq

    name = normalize_name(drug_name)
    if not name:
        return None
    candidates = conn.execute(text("""
        SELECT item_seq FROM pill_catalog
        WHERE item_name_normalized = :name AND sale_status = 'NORMAL'
        LIMIT 2
    """), {"name": name}).scalars().all()
    return candidates[0] if len(candidates) == 1 else None


@router.get("/search")
def search(q: str = Query(..., min_length=1, max_length=100, description="약 이름 (띄어쓰기·괄호 무시)"),
           page: int = Query(1, ge=1), size: int = Query(20, ge=1, le=50)):
    """
    약 이름 검색. 띄어 쓴 단어가 "모두" 들어 있는 약을 찾는다.
      예) "타이레놀 500" → 이름에 "타이레놀" 과 "500" 이 모두 있는 약 (타이레놀정500mg 등)
    첫 단어로 "시작"하는 약을 먼저, 그다음 판매 중인 약, 알약 순으로 보여 준다.
    """
    words = [w for w in (normalize_name(part) for part in q.split()) if w][:5]
    if not words:
        raise BusinessError(ErrorCode.COMMON_INVALID_INPUT, "검색어를 입력해 주세요.")
    params = {f"w{i}": f"%{escape_like(w)}%" for i, w in enumerate(words)}
    conditions = " AND ".join(f"p.item_name_normalized LIKE :w{i}" for i in range(len(words)))
    params.update({"prefix": f"{escape_like(words[0])}%",
                   # 다음 페이지가 있는지 알기 위해 1개 더 가져온다
                   "limit": size + 1, "offset": (page - 1) * size})
    with engine.connect() as conn:
        rows = rows_to_dicts(conn.execute(text(f"""
            SELECT {SUMMARY_COLUMNS}
            FROM pill_catalog p
            WHERE {conditions}
            ORDER BY (p.item_name_normalized LIKE :prefix) DESC,
                     (p.sale_status = 'NORMAL') DESC,
                     p.is_pill DESC,
                     p.item_name
            LIMIT :limit OFFSET :offset
        """), params))
    return ok({"items": rows[:size], "page": page, "size": size, "has_next": len(rows) > size})


@router.get("/{item_seq}")
def detail(item_seq: str):
    """
    약 상세 : 기본 정보 + 성분 + 보험코드 목록 + DUR 주의 정보
    dur 형식 : {"pregnancy": [...], "age": [...], "elderly": [...], "combination": [...]}
               (해당 주의 사항이 없으면 빈 목록)
    """
    with engine.connect() as conn:
        row = conn.execute(text(f"""
            SELECT {SUMMARY_COLUMNS}, p.main_ingredient, p.chart,
                   p.print_front, p.print_back, p.line_front, p.line_back,
                   p.leng_long, p.leng_short, p.thick
            FROM pill_catalog p WHERE p.item_seq = :item_seq
        """), {"item_seq": item_seq}).first()
        if row is None:
            raise BusinessError(ErrorCode.CATALOG_NOT_FOUND)
        codes = conn.execute(text(
            "SELECT code FROM pill_catalog_code WHERE item_seq = :item_seq AND code_type = 'EDI' ORDER BY code"
        ), {"item_seq": item_seq}).scalars().all()
        dur_rows = conn.execute(text("""
            SELECT dur_type, partner_ingredient, grade, age_condition, detail
            FROM pill_catalog_dur WHERE item_seq = :item_seq
            ORDER BY id
        """), {"item_seq": item_seq}).all()
    data = dict(row._mapping)
    data["edi_codes"] = codes
    # DUR 종류별로 묶는다 (화면에서 임부금기 → 연령금기 → 노인주의 → 병용금기 순으로 보여 준다)
    data["dur"] = {"pregnancy": [], "age": [], "elderly": [], "combination": []}
    for dur in dur_rows:
        data["dur"][dur.dur_type.lower()].append({
            "partner_ingredient": dur.partner_ingredient, "grade": dur.grade,
            "age_condition": dur.age_condition, "detail": dur.detail})
    return ok(data)
