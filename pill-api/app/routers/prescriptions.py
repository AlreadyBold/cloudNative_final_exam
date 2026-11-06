"""
저장한 처방내역 API (로그인 필요)
  GET    /api/pill/prescriptions       : 저장한 처방내역 목록 (약 포함)
  POST   /api/pill/prescriptions       : 불러온 처방 여러 건을 한 번에 저장 (이미 저장한 처방은 건너뜀)
  DELETE /api/pill/prescriptions/{id}  : 삭제 (안의 약도 함께 삭제됨, FK ON DELETE CASCADE)

처방내역 "불러오기"(간편인증)는 user-api 가 하고, 불러온 결과 중 사용자가 고른 것을 여기서 "저장"한다.
요청 본문은 user-api 의 완료 응답(source, prescriptions)과 같은 모양이라 프론트가 고른 처방만 그대로 넘기면 된다.

[중복 기준]
CODEF 조제 내역에는 조제 "시각"이나 처방전 번호 같은 고유 값이 없고 조제일만 있다.
그래서 조제일 · 약국 · 병원 · 약 목록(보험코드, 없으면 약품명)이 모두 같을 때만 같은 처방으로 본다.
같은 날 같은 병원에 두 번 가서 다른 약을 받은 경우는 따로 저장된다.
"""
import datetime
from typing import List, Literal, Optional

from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field
from sqlalchemy import bindparam, text

from app.db import engine, rows_to_dicts
from app.errors import BusinessError, ErrorCode, ok
from app.names import normalize_name
from app.routers.catalog import match_catalog
from app.session import LoginUser, login_required

router = APIRouter(prefix="/api/pill/prescriptions", tags=["저장한 처방내역"])


class DrugIn(BaseModel):
    """처방 안의 약 1개 (user-api 응답의 drugs[i] 와 같은 필드 이름)"""
    drug_code: Optional[str] = Field(None, max_length=20)
    name: str = Field(..., min_length=1, max_length=500)
    ingredients: Optional[str] = Field(None, max_length=1000)
    one_dose: Optional[str] = Field(None, max_length=20)
    daily_doses: Optional[str] = Field(None, max_length=20)
    total_dosing_days: Optional[str] = Field(None, max_length=20)
    effect: Optional[str] = Field(None, max_length=200)
    formula: Optional[str] = Field(None, max_length=500)


class PrescriptionIn(BaseModel):
    """처방 1건 (user-api 응답의 prescriptions[i] 와 같은 필드 이름)"""
    dispensed_date: Optional[datetime.date] = None
    pharmacy: Optional[str] = Field(None, max_length=100)
    prescriber: Optional[str] = Field(None, max_length=100)
    drugs: List[DrugIn] = Field(..., min_length=1, max_length=100)


class SaveRequest(BaseModel):
    """저장 요청. 예) {"source": "MOCK", "prescriptions": [{...}, {...}]}"""
    source: Literal["CODEF", "MOCK"]
    prescriptions: List[PrescriptionIn] = Field(..., min_length=1, max_length=200)


def drug_signature(codes_and_names):
    """약 목록 → 비교용 값. 보험코드가 있으면 코드, 없으면 정규화한 약품명. 순서는 무시한다."""
    return tuple(sorted((code or "").strip() or "name:" + normalize_name(name) for code, name in codes_and_names))


def prescription_key(dispensed_date, pharmacy, prescriber, signature):
    """중복 판단 기준 : 조제일 · 약국 · 병원 · 약 목록"""
    return (str(dispensed_date) if dispensed_date else None, pharmacy or None, prescriber or None, signature)


def saved_keys(conn, user_id):
    """이미 저장한 처방들의 중복 판단 기준 값 모음"""
    rows = conn.execute(text("""
        SELECT s.id, s.dispensed_date, s.pharmacy, s.prescriber, d.drug_code, d.drug_name
        FROM saved_prescriptions s
        JOIN saved_prescription_drugs d ON d.prescription_id = s.id
        WHERE s.user_id = :user_id
    """), {"user_id": user_id}).all()
    grouped = {}
    for row in rows:
        entry = grouped.setdefault(row.id, [row.dispensed_date, row.pharmacy, row.prescriber, []])
        entry[3].append((row.drug_code, row.drug_name))
    return {prescription_key(date, pharmacy, prescriber, drug_signature(drugs))
            for date, pharmacy, prescriber, drugs in grouped.values()}


def load_prescriptions(conn, user_id):
    """처방 목록 + 각 처방의 약(카탈로그 사진 포함). 쿼리 2번으로 가져와 파이썬에서 묶는다."""
    prescriptions = rows_to_dicts(conn.execute(text("""
        SELECT id, dispensed_date, pharmacy, prescriber, data_source, created_at
        FROM saved_prescriptions WHERE user_id = :user_id
        ORDER BY dispensed_date DESC, id DESC
    """), {"user_id": user_id}))
    if not prescriptions:
        return []
    drugs = rows_to_dicts(conn.execute(text("""
        SELECT d.id, d.prescription_id, d.drug_code, d.drug_name, d.ingredients, d.one_dose, d.daily_doses,
               d.total_dosing_days, d.effect, d.formula, d.item_seq, p.image_url, p.drug_shape, p.color1
        FROM saved_prescription_drugs d
        LEFT JOIN pill_catalog p ON p.item_seq = d.item_seq
        WHERE d.prescription_id IN :ids
        ORDER BY d.id
    """).bindparams(bindparam("ids", expanding=True)), {"ids": [p["id"] for p in prescriptions]}))
    by_id = {p["id"]: {**p, "drugs": []} for p in prescriptions}
    for drug in drugs:
        by_id[drug.pop("prescription_id")]["drugs"].append(drug)
    return list(by_id.values())


@router.get("")
def list_prescriptions(user: LoginUser = Depends(login_required)):
    """저장한 처방내역 (최근 조제일 순)"""
    with engine.connect() as conn:
        return ok(load_prescriptions(conn, user.user_id))


@router.post("", status_code=201)
def save_prescriptions(body: SaveRequest, user: LoginUser = Depends(login_required)):
    """
    불러온 처방 여러 건을 한 번에 저장. 이미 저장한 처방(같은 요청 안의 중복 포함)은 건너뛴다.
    응답 예) {"saved_count": 2, "skipped_count": 1, "saved": [저장된 처방들]}
    """
    with engine.begin() as conn:
        existing = saved_keys(conn, user.user_id)
        saved_ids, skipped = [], 0
        for prescription in body.prescriptions:
            key = prescription_key(prescription.dispensed_date, prescription.pharmacy, prescription.prescriber,
                                   drug_signature((d.drug_code, d.name) for d in prescription.drugs))
            if key in existing:
                skipped += 1
                continue
            existing.add(key)

            prescription_id = conn.execute(text("""
                INSERT INTO saved_prescriptions (user_id, dispensed_date, pharmacy, prescriber, data_source)
                VALUES (:user_id, :date, :pharmacy, :prescriber, :source)
            """), {"user_id": user.user_id, "date": prescription.dispensed_date, "pharmacy": prescription.pharmacy,
                   "prescriber": prescription.prescriber, "source": body.source}).lastrowid
            conn.execute(text("""
                INSERT INTO saved_prescription_drugs
                    (prescription_id, drug_code, drug_name, ingredients, one_dose, daily_doses,
                     total_dosing_days, effect, formula, item_seq)
                VALUES (:pid, :drug_code, :name, :ingredients, :one_dose, :daily_doses,
                        :total_dosing_days, :effect, :formula, :item_seq)
            """), [{**drug.model_dump(), "pid": prescription_id,
                    # 보험코드 → 약품명 순으로 카탈로그 약을 찾아 연결 (못 찾으면 NULL, 처방 정보만 보관)
                    "item_seq": match_catalog(conn, drug.drug_code, drug.name)} for drug in prescription.drugs])
            saved_ids.append(prescription_id)

        saved = [p for p in load_prescriptions(conn, user.user_id) if p["id"] in set(saved_ids)]
    return ok({"saved_count": len(saved_ids), "skipped_count": skipped, "saved": saved})


@router.delete("/{prescription_id}")
def delete_prescription(prescription_id: int, user: LoginUser = Depends(login_required)):
    """저장한 처방 삭제"""
    with engine.begin() as conn:
        deleted = conn.execute(text("DELETE FROM saved_prescriptions WHERE id = :id AND user_id = :user_id"),
                               {"id": prescription_id, "user_id": user.user_id}).rowcount
    if deleted == 0:
        raise BusinessError(ErrorCode.PRESCRIPTION_NOT_FOUND)
    return ok()
