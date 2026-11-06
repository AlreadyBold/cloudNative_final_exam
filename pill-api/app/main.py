"""
alyak-pill-api 시작점 (FastAPI)

담당 기능 : 약 카탈로그 검색·상세, 내 약통, 저장한 처방내역
로그인    : 직접 하지 않는다. user-api(Spring) 가 Redis 에 저장한 세션을 읽어서 로그인 사용자를 안다 (app/session.py)
실행      : uvicorn app.main:app --host 0.0.0.0 --port 8000   (Dockerfile.pill-api 의 CMD)
API 문서  : /api/pill/docs  (FastAPI 가 자동으로 만들어 주는 Swagger 화면)
"""
import logging

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from app import config
from app.errors import BusinessError, ErrorCode, fail
from app.routers import catalog, me, pillbox, prescriptions

log = logging.getLogger("alyak-pill-api")

app = FastAPI(
    title="내약통한눈에 pill-api",
    description="약 카탈로그 · 내 약통 · 저장한 처방내역 (로그인은 user-api 의 Redis 세션 공유)",
    # 프록시를 거쳐도 열리도록 문서 주소를 /api/pill 아래로
    docs_url="/api/pill/docs",
    openapi_url="/api/pill/openapi.json",
    redoc_url=None,
)

app.include_router(me.router)
app.include_router(catalog.router)
app.include_router(pillbox.router)
app.include_router(prescriptions.router)


@app.middleware("http")
async def add_served_by_header(request: Request, call_next):
    """모든 응답에 X-Served-By: <컨테이너 hostname> 헤더 (user-api 와 같은 이름)"""
    response = await call_next(request)
    response.headers["X-Served-By"] = config.SERVED_BY
    return response


# ----- 예외 → 공통 실패 응답 (user-api 의 GlobalExceptionHandler 와 같은 역할) -----

@app.exception_handler(BusinessError)
async def handle_business(request: Request, error: BusinessError):
    return JSONResponse(status_code=error.error_code.status, content=fail(error.error_code, error.message))


@app.exception_handler(RequestValidationError)
async def handle_validation(request: Request, error: RequestValidationError):
    """요청 값 검증 실패 → 400. 어떤 필드가 틀렸는지 알려 준다."""
    fields = sorted({".".join(str(p) for p in e["loc"][1:]) or str(e["loc"][0]) for e in error.errors()})
    return JSONResponse(status_code=400, content=fail(
        ErrorCode.COMMON_INVALID_INPUT, "요청 값이 올바르지 않습니다: " + ", ".join(fields)))


@app.exception_handler(StarletteHTTPException)
async def handle_http(request: Request, error: StarletteHTTPException):
    """없는 주소(404), 잘못된 메서드(405) 등"""
    code = {404: ErrorCode.COMMON_NOT_FOUND, 405: ErrorCode.COMMON_METHOD_NOT_ALLOWED}.get(
        error.status_code, ErrorCode.COMMON_INVALID_INPUT)
    return JSONResponse(status_code=error.status_code, content=fail(code))


@app.exception_handler(Exception)
async def handle_unexpected(request: Request, error: Exception):
    """예상하지 못한 오류 → 내용은 로그에만, 응답에는 일반 메시지만 (정보 노출 방지)"""
    log.exception("처리하지 못한 예외")
    return JSONResponse(status_code=500, content=fail(ErrorCode.COMMON_INTERNAL_ERROR),
                        headers={"X-Served-By": config.SERVED_BY})
