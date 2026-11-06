#!/bin/bash
# =====================================================================
# 내약통한눈에 DB 초기화 스크립트 (2/3) : 서비스별 DB 계정 생성
#
#  - 비밀번호를 코드에 적지 않고 환경변수로 받기 위해 SQL 이 아니라 셸 스크립트로 만든다.
#    (SQL 파일은 환경변수를 읽을 수 없다)
#  - 값은 docker run --env-file env/mysql.env 로 들어온다. (scripts/00_env.sh 가 무작위로 생성)
#      USER_DB_PASSWORD : user-api 계정 alyak_user_app 의 비밀번호
#      PILL_DB_PASSWORD : pill-api 계정 alyak_pill_app 의 비밀번호
#  - mysql 공식 이미지의 entrypoint 가 이 파일을 source 해서 실행하므로
#    entrypoint 의 docker_process_sql 함수(root 로 SQL 실행)를 그대로 쓸 수 있다.
# =====================================================================

# 비밀번호가 없으면 계정을 만들지 않고 초기화를 실패시킨다 (빈 비밀번호 계정이 생기는 것을 막는다)
for name in USER_DB_PASSWORD PILL_DB_PASSWORD; do
	if [ -z "${!name}" ]; then
		echo "[02_create_accounts.sh] 환경변수 ${name} 가 없습니다. env/mysql.env 를 확인하세요." >&2
		exit 1
	fi
done

# SQL 문자열 안에서 작은따옴표가 문법을 깨지 않도록 '' 로 바꾼다
user_pw=${USER_DB_PASSWORD//\'/\'\'}
pill_pw=${PILL_DB_PASSWORD//\'/\'\'}

# '%' : 같은 도커 네트워크(alyak-net)의 다른 컨테이너에서 접속 허용
# 데이터 조회·추가·수정·삭제(DML)만 허용하고, 테이블 구조 변경(DDL) 권한은 주지 않는다.
docker_process_sql <<-EOSQL
	CREATE USER IF NOT EXISTS 'alyak_user_app'@'%' IDENTIFIED BY '${user_pw}';
	GRANT SELECT, INSERT, UPDATE, DELETE ON alyak_user.* TO 'alyak_user_app'@'%';

	CREATE USER IF NOT EXISTS 'alyak_pill_app'@'%' IDENTIFIED BY '${pill_pw}';
	GRANT SELECT, INSERT, UPDATE, DELETE ON alyak_pill.* TO 'alyak_pill_app'@'%';

	FLUSH PRIVILEGES;
EOSQL
