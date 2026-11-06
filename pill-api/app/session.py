"""
★ Spring(user-api) 이 만든 로그인 세션을 파이썬에서 읽는 부분 (이 과제의 핵심).

[흐름]
 1. 사용자가 user-api 로 로그인 → Spring Session 이 Redis 에 세션을 저장하고 브라우저에 SESSION 쿠키를 준다
 2. 브라우저가 /api/pill/** 을 호출할 때 같은 SESSION 쿠키가 함께 온다 (쿠키 path=/)
 3. 이 모듈이 쿠키 값(세션 ID)으로 Redis 를 직접 조회해서 로그인한 회원을 알아낸다

[Redis 저장 형태] (user-api 의 SessionConfig.java 가 정한 규칙)
 키   : alyak:session:sessions:<세션ID>   (Hash 자료형)
 필드 : sessionAttr:userId    → 1                (JSON 숫자)
        sessionAttr:username  → "a@b.com"        (JSON 문자열)
        sessionAttr:nickname  → "준형"           (JSON 문자열)
        lastAccessedTime      → 1793759808488    (밀리초)
 값이 JSON 이라서 json.loads 로 바로 읽을 수 있다. (Spring 기본값인 자바 직렬화였다면 파이썬이 읽을 수 없다)
"""
import json
import re
import time
from dataclasses import dataclass

import redis
from fastapi import Request

from app import config
from app.errors import BusinessError, ErrorCode

# Redis 연결 (요청마다 새로 만들지 않고 커넥션 풀을 공유)
redis_client = redis.Redis(host=config.REDIS_HOST, port=config.REDIS_PORT, decode_responses=True)

# Spring Session 의 세션 ID 는 UUID 형식. 다른 모양의 쿠키 값은 Redis 를 조회하지 않고 바로 거절
SESSION_ID_PATTERN = re.compile(r"^[0-9a-fA-F-]{36}$")


@dataclass
class LoginUser:
    """현재 요청을 보낸 로그인 사용자"""
    user_id: int
    username: str
    nickname: str
    session_id: str

    def to_dict(self):
        return {"user_id": self.user_id, "username": self.username, "nickname": self.nickname}


def find_login_user(request: Request):
    """쿠키 → Redis 세션 → LoginUser. 로그인하지 않았거나 세션이 만료됐으면 None"""
    session_id = request.cookies.get(config.SESSION_COOKIE_NAME)
    if not session_id or not SESSION_ID_PATTERN.match(session_id):
        return None

    key = config.SESSION_KEY_PREFIX + session_id
    session = redis_client.hgetall(key)  # 만료된 세션은 Redis 가 이미 지웠으므로 빈 dict
    raw_user_id = session.get("sessionAttr:userId")
    if raw_user_id is None:
        return None  # 세션은 있지만 로그인 전 세션

    # 세션 만료 시간 연장 : pill-api 만 계속 써도 로그인이 30분 뒤에 풀리지 않도록
    # Spring 과 같은 방식으로 마지막 접근 시각을 고치고 Redis 키 만료 시간을 다시 건다
    redis_client.hset(key, "lastAccessedTime", str(int(time.time() * 1000)))
    redis_client.expire(key, config.SESSION_MAX_INACTIVE_SECONDS)

    return LoginUser(
        user_id=int(json.loads(raw_user_id)),
        username=json.loads(session.get("sessionAttr:username", "null")),
        nickname=json.loads(session.get("sessionAttr:nickname", "null")),
        session_id=session_id,
    )


def login_required(request: Request):
    """
    로그인이 필요한 API 에 붙이는 의존성(Depends). 로그인하지 않았으면 401.
    사용 예) def my_api(user: LoginUser = Depends(login_required)): ...
    """
    user = find_login_user(request)
    if user is None:
        raise BusinessError(ErrorCode.COMMON_UNAUTHORIZED)
    return user
