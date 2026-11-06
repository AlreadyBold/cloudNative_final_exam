#!/bin/bash
# =====================================================================
# 00_env.sh : 비밀값(env 파일) 만들기 — 가장 먼저 한 번 실행
#
#  - DB 비밀번호는 코드·이미지·git 에 두지 않는다. 이 스크립트가 무작위 값으로 env 파일을 만들고,
#    docker run --env-file 로 각 컨테이너에 "자기에게 필요한 값만" 넘긴다.
#      env/mysql.env    : MySQL root 비밀번호 + 서비스 계정 2개의 비밀번호 (계정 생성용)
#      env/user-api.env : user-api 계정 비밀번호 (+ CODEF 키는 직접 채워 넣는 선택 항목)
#      env/pill-api.env : pill-api 계정 비밀번호
#  - env/*.env 는 .gitignore 에 들어 있어 커밋되지 않는다. (형식은 env/*.env.example 참고)
#  - 이미 파일이 있으면 덮어쓰지 않는다. 비밀번호가 바뀌면 이미 만들어진 DB 에 접속할 수 없기 때문이다.
#    (처음부터 다시 하려면 env/*.env 와 ~/alyak/mysql-data 를 함께 지운다)
# =====================================================================
set -e
cd "$(dirname "$0")/.."   # 저장소 최상위 폴더로 이동

if [ -f env/mysql.env ] || [ -f env/user-api.env ] || [ -f env/pill-api.env ]; then
	echo "env 파일이 이미 있어서 그대로 둡니다. (env/mysql.env, env/user-api.env, env/pill-api.env)"
	exit 0
fi

# 영문 대소문자+숫자 32자 무작위 문자열 (특수문자를 빼서 SQL·셸에서 따옴표 문제가 없게)
random_password() {
	tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 32
}

ROOT_PW=$(random_password)
USER_PW=$(random_password)
PILL_PW=$(random_password)

# umask 077 : 만든 파일을 나만 읽고 쓸 수 있게 (권한 600)
umask 077
mkdir -p env

cat > env/mysql.env <<ENV
MYSQL_ROOT_PASSWORD=${ROOT_PW}
USER_DB_PASSWORD=${USER_PW}
PILL_DB_PASSWORD=${PILL_PW}
ENV

cat > env/user-api.env <<ENV
DB_PASSWORD=${USER_PW}
# CODEF 키 (선택). 비워 두면 처방내역 불러오기가 모의(MOCK) 모드로 동작한다.
CODEF_SERVICE_TYPE=DEMO
CODEF_CLIENT_ID=
CODEF_CLIENT_SECRET=
CODEF_PUBLIC_KEY=
ENV

cat > env/pill-api.env <<ENV
DB_PASSWORD=${PILL_PW}
ENV

echo "env/mysql.env, env/user-api.env, env/pill-api.env 를 만들었습니다. (비밀번호는 무작위, 화면에 출력하지 않음)"
