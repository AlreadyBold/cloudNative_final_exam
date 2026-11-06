"""
MySQL 접속. SQLAlchemy 의 커넥션 풀만 쓰고, 쿼리는 SQL 을 직접 써서 무엇을 조회하는지 바로 보이게 했다.

테이블은 db/initdb 의 SQL 이 만든다. 이 서버는 alyak_pill 스키마만 볼 수 있다 (회원 테이블 접근 권한 없음).
"""
from sqlalchemy import create_engine

from app import config

engine = create_engine(
    "mysql+pymysql://{user}:{pw}@{host}:{port}/{db}?charset=utf8mb4".format(
        user=config.DB_USERNAME, pw=config.DB_PASSWORD,
        host=config.DB_HOST, port=config.DB_PORT, db=config.DB_NAME),
    # 커넥션을 꺼낼 때 살아 있는지 먼저 확인 (DB 컨테이너를 재시작해도 오류 없이 다시 연결)
    pool_pre_ping=True,
    # 1시간 지난 커넥션은 새로 만든다 (MySQL 이 오래된 연결을 끊는 것에 대비)
    pool_recycle=3600,
)


def rows_to_dicts(result):
    """쿼리 결과 → dict 목록 (JSON 응답으로 바로 쓸 수 있게)"""
    return [dict(row._mapping) for row in result]
