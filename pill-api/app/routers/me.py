"""
세션 확인 · 내 데이터 API
  GET    /api/pill/health  : 서버 상태 (로그인 불필요)
  GET    /api/pill/me      : 세션에서 읽은 로그인 사용자 + 이 요청을 처리한 서버
  DELETE /api/pill/me/data : 내 약통·저장한 처방내역 전체 삭제 (회원 탈퇴 직전에 프론트가 호출)
"""
from fastapi import APIRouter, Depends
from sqlalchemy import text

from app import config
from app.db import engine
from app.errors import ok
from app.session import LoginUser, login_required

router = APIRouter(prefix="/api/pill", tags=["세션 확인"])


@router.get("/health")
def health():
    return ok({"status": "UP", "served_by": config.SERVED_BY, "service": config.SERVICE_NAME})


@router.get("/me")
def me(user: LoginUser = Depends(login_required)):
    """
    user-api 의 GET /api/user/me 와 같은 필드로 응답한다.
    프론트의 "세션 확인" 패널에서 두 응답을 나란히 놓으면
    "같은 사용자 · 같은 세션 ID · 다른 서버(자바/파이썬)" 임을 확인할 수 있다.
    """
    return ok({
        "login_user": user.to_dict(),
        "session_id_prefix": user.session_id[:8],
        "served_by": config.SERVED_BY,
        "service": config.SERVICE_NAME,
    })


@router.delete("/me/data")
def delete_my_data(user: LoginUser = Depends(login_required)):
    """회원 탈퇴 전 정리 : 이 서버가 가진 내 데이터를 모두 지운다 (처방 안의 약은 FK CASCADE 로 함께 삭제)"""
    with engine.begin() as conn:
        pillbox = conn.execute(text("DELETE FROM pillbox_items WHERE user_id = :u"), {"u": user.user_id}).rowcount
        prescriptions = conn.execute(text("DELETE FROM saved_prescriptions WHERE user_id = :u"),
                                     {"u": user.user_id}).rowcount
    return ok({"deleted_pillbox_items": pillbox, "deleted_prescriptions": prescriptions})
