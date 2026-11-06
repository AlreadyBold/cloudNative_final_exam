"""
설정값. docker run 의 -e 옵션(환경변수)으로 바꿀 수 있고, 없으면 기본값을 쓴다.

기본값은 같은 도커 네트워크(alyak-net)의 컨테이너 이름을 가리킨다.
도커 내장 DNS 가 alyak-mysql, alyak-redis 같은 이름을 컨테이너 IP 로 바꿔 준다.
"""
import os
import socket

# ----- DB : alyak-mysql 의 alyak_pill 스키마 (pill-api 전용 계정, 조회·추가·수정·삭제 권한만) -----
DB_HOST = os.getenv("DB_HOST", "alyak-mysql")
DB_PORT = int(os.getenv("DB_PORT", "3306"))
DB_NAME = os.getenv("DB_NAME", "alyak_pill")
DB_USERNAME = os.getenv("DB_USERNAME", "alyak_pill_app")
# 과제 시연용 로컬 DB 비밀번호 (db/initdb/01_create_schemas_accounts_tables.sql 과 같은 값)
DB_PASSWORD = os.getenv("DB_PASSWORD", "REMOVED")

# ----- Redis : user-api(Spring) 와 같은 세션 저장소 -----
REDIS_HOST = os.getenv("REDIS_HOST", "alyak-redis")
REDIS_PORT = int(os.getenv("REDIS_PORT", "6379"))

# ----- 세션 규칙 : user-api 의 SessionConfig.java 와 반드시 같아야 한다 -----
# 쿠키 이름
SESSION_COOKIE_NAME = "SESSION"
# Redis 키 = SESSION_KEY_PREFIX + 쿠키 값(세션 ID)
SESSION_KEY_PREFIX = "alyak:session:sessions:"
# 세션 유효 시간(초). 마지막 요청 후 30분
SESSION_MAX_INACTIVE_SECONDS = 1800

# 이 컨테이너의 hostname (docker run --hostname alyak-pill-api). 응답의 served_by 에 쓴다
SERVED_BY = os.getenv("HOSTNAME") or socket.gethostname()
SERVICE_NAME = "pill-api (FastAPI)"
