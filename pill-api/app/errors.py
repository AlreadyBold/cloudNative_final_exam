"""
공통 응답 형식과 에러 코드.

user-api(Spring) 의 ApiResponse 와 똑같은 모양으로 응답해서, 프론트가 두 서버를 같은 코드로 다룰 수 있게 했다.
  성공 : {"success": true,  "data": {...}, "error": null}
  실패 : {"success": false, "data": null,  "error": {"code": "...", "message": "..."}}
"""
from enum import Enum


class ErrorCode(Enum):
    """(HTTP 상태코드, 사용자에게 보여 줄 메시지)"""

    # ----- 공통 -----
    COMMON_INVALID_INPUT = (400, "요청 값이 올바르지 않습니다.")
    COMMON_UNAUTHORIZED = (401, "로그인이 필요합니다.")
    COMMON_NOT_FOUND = (404, "요청한 주소를 찾을 수 없습니다.")
    COMMON_METHOD_NOT_ALLOWED = (405, "지원하지 않는 HTTP 메서드입니다.")
    COMMON_INTERNAL_ERROR = (500, "서버 오류가 발생했습니다.")

    # ----- 약 카탈로그 -----
    CATALOG_NOT_FOUND = (404, "약 정보를 찾을 수 없습니다.")

    # ----- 내 약통 -----
    PILLBOX_ITEM_NOT_FOUND = (404, "내 약통에서 약을 찾을 수 없습니다.")
    PILLBOX_DUPLICATE = (409, "이미 내 약통에 담긴 약입니다.")

    # ----- 저장한 처방내역 -----
    PRESCRIPTION_NOT_FOUND = (404, "저장한 처방내역을 찾을 수 없습니다.")

    @property
    def status(self):
        return self.value[0]

    @property
    def message(self):
        return self.value[1]


class BusinessError(Exception):
    """예상 가능한 실패를 알리는 예외. main.py 의 핸들러가 공통 실패 응답으로 바꾼다."""

    def __init__(self, error_code, message=None):
        super().__init__(message or error_code.message)
        self.error_code = error_code
        self.message = message or error_code.message


def ok(data=None):
    """성공 응답 본문"""
    return {"success": True, "data": data, "error": None}


def fail(error_code, message=None):
    """실패 응답 본문"""
    return {"success": False, "data": None,
            "error": {"code": error_code.name, "message": message or error_code.message}}
