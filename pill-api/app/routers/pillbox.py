"""
내 약통 API (로그인 필요) : 사용자가 "지금 먹고 있는 약"으로 담아 둔 목록
  GET    /api/pill/pillbox       : 목록
  POST   /api/pill/pillbox       : 담기 (약 검색 결과 또는 처방내역에서)
  PATCH  /api/pill/pillbox/{id}  : 메모 수정
  DELETE /api/pill/pillbox/{id}  : 빼기

회원 번호(user_id)는 요청 본문이 아니라 "세션"에서 가져온다. 그래서 다른 사람의 약통은 볼 수도, 고칠 수도 없다.
"""
from typing import Literal, Optional

from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field
from sqlalchemy import text

from app.db import engine, rows_to_dicts
from app.errors import BusinessError, ErrorCode, ok
from app.routers.catalog import match_catalog
from app.session import LoginUser, login_required

router = APIRouter(prefix="/api/pill/pillbox", tags=["내 약통"])

# 약통 항목 + 카탈로그 정보(사진, 모양). 카탈로그에 없는 약도 보여 주기 위해 LEFT JOIN
SELECT_ITEMS = """
    SELECT b.id, b.item_seq, b.drug_code, b.drug_name, b.memo, b.source, b.created_at,
           p.image_url, p.entp_name, p.drug_shape, p.color1, p.etc_otc, p.class_name
    FROM pillbox_items b
    LEFT JOIN pill_catalog p ON p.item_seq = b.item_seq
"""


class AddRequest(BaseModel):
    """
    담기 요청. 셋 중 하나는 있어야 한다.
      약 검색에서 담기    : {"item_seq": "200300406", "source": "SEARCH"}
      처방내역에서 담기   : {"drug_code": "073400340", "drug_name": "리피토정10밀리그램", "source": "PRESCRIPTION"}
    """
    item_seq: Optional[str] = Field(None, max_length=20)
    drug_code: Optional[str] = Field(None, max_length=20)
    drug_name: Optional[str] = Field(None, max_length=500)
    memo: Optional[str] = Field(None, max_length=200)
    source: Literal["SEARCH", "PRESCRIPTION"]


class MemoRequest(BaseModel):
    """메모 수정. 예) {"memo": "아침 식후 1알"} (빈 값이면 메모 삭제)"""
    memo: Optional[str] = Field(None, max_length=200)


def find_item(conn, user_id, item_id):
    row = conn.execute(text(SELECT_ITEMS + " WHERE b.id = :id AND b.user_id = :user_id"),
                       {"id": item_id, "user_id": user_id}).first()
    if row is None:
        raise BusinessError(ErrorCode.PILLBOX_ITEM_NOT_FOUND)
    return dict(row._mapping)


@router.get("")
def list_items(user: LoginUser = Depends(login_required)):
    """내 약통 목록 (최근에 담은 순)"""
    with engine.connect() as conn:
        rows = rows_to_dicts(conn.execute(
            text(SELECT_ITEMS + " WHERE b.user_id = :user_id ORDER BY b.created_at DESC, b.id DESC"),
            {"user_id": user.user_id}))
    return ok(rows)


@router.post("", status_code=201)
def add_item(body: AddRequest, user: LoginUser = Depends(login_required)):
    """약통에 약 담기. 같은 약을 두 번 담으면 409."""
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

        # 중복 확인 : 같은 카탈로그 약, 또는 (카탈로그에 없으면) 같은 보험코드
        duplicate = conn.execute(text("""
            SELECT 1 FROM pillbox_items
            WHERE user_id = :user_id
              AND ((:item_seq IS NOT NULL AND item_seq = :item_seq)
                OR (:item_seq IS NULL AND :drug_code IS NOT NULL AND drug_code = :drug_code))
            LIMIT 1
        """), {"user_id": user.user_id, "item_seq": item_seq, "drug_code": body.drug_code}).first()
        if duplicate:
            raise BusinessError(ErrorCode.PILLBOX_DUPLICATE)

        result = conn.execute(text("""
            INSERT INTO pillbox_items (user_id, item_seq, drug_code, drug_name, memo, source)
            VALUES (:user_id, :item_seq, :drug_code, :drug_name, :memo, :source)
        """), {"user_id": user.user_id, "item_seq": item_seq, "drug_code": body.drug_code,
               "drug_name": drug_name, "memo": (body.memo or "").strip() or None, "source": body.source})
        return ok(find_item(conn, user.user_id, result.lastrowid))


@router.patch("/{item_id}")
def update_memo(item_id: int, body: MemoRequest, user: LoginUser = Depends(login_required)):
    """메모 수정"""
    with engine.begin() as conn:
        find_item(conn, user.user_id, item_id)  # 내 약통의 항목인지 확인 (아니면 404)
        conn.execute(text("UPDATE pillbox_items SET memo = :memo WHERE id = :id AND user_id = :user_id"),
                     {"memo": (body.memo or "").strip() or None, "id": item_id, "user_id": user.user_id})
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
