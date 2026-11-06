#!/bin/bash
# =====================================================================
# 03_run.sh : 내약통한눈에 컨테이너 실행 (실행 순서 = 의존 관계 순서)
#
#  공통 옵션 설명
#   -d                         : 백그라운드(detached)로 실행
#   --name <이름>              : 컨테이너 이름 (같은 네트워크에서 이 이름이 곧 접속 주소)
#   --hostname <이름>          : 컨테이너 안의 hostname. 서버 응답의 served_by 에 그대로 찍힌다
#   --network alyak-net        : 01_network.sh 에서 만든 사용자 정의 네트워크에 연결
#   --restart unless-stopped   : VM 재부팅·오류 종료 시 자동 재시작 (직접 stop 한 경우는 제외)
# =====================================================================
set -e
cd "$(dirname "$0")/.."   # 저장소 최상위 폴더로 이동 (env/ 경로 기준)

# [1] DB : MySQL
#  -p 33307:3306  : 호스트 33307 → 컨테이너 3306 (확인용. 33306 은 수업 실습 mysql 이 사용 중)
#  --env-file env/mysql.env : root 비밀번호와 서비스 계정 비밀번호 (scripts/00_env.sh 가 만든 파일, 최초 초기화 때 사용)
#  -v ~/alyak/mysql-data:/var/lib/mysql : DB 데이터 폴더를 호스트에 바인드 마운트 → 컨테이너를 지워도 데이터 유지
mkdir -p ~/alyak/mysql-data
docker run -d --name alyak-mysql --hostname alyak-mysql --network alyak-net --restart unless-stopped -p 33307:3306 --env-file env/mysql.env -v ~/alyak/mysql-data:/var/lib/mysql junheong/alyak-mysql:1.0

# [2] 세션 저장소 : Redis 공식 이미지 (버전 고정)
#  호스트 포트를 열지 않는다. 같은 네트워크의 서버들만 alyak-redis:6379 로 접속한다.
docker run -d --name alyak-redis --hostname alyak-redis --network alyak-net --restart unless-stopped redis:8.10.3

# [3] 회원 서버 2대 : 같은 이미지로 컨테이너 2개 (프록시가 번갈아 요청을 보낸다)
#  호스트 포트를 열지 않는다. alyak-proxy 만 alyak-user-api-1:8080, alyak-user-api-2:8080 으로 접속한다.
#  --hostname 을 다르게 줘서 응답의 served_by / X-Served-By 로 어느 컨테이너가 처리했는지 구분한다.
#  --env-file env/user-api.env : DB 비밀번호 (+ 선택 CODEF 키. 비워 두면 모의(MOCK) 모드)
docker run -d --name alyak-user-api-1 --hostname alyak-user-api-1 --network alyak-net --restart unless-stopped --env-file env/user-api.env junheong/alyak-user-api:1.0
docker run -d --name alyak-user-api-2 --hostname alyak-user-api-2 --network alyak-net --restart unless-stopped --env-file env/user-api.env junheong/alyak-user-api:1.0

# [4] 약 서버 1대 : FastAPI
#  호스트 포트를 열지 않는다. alyak-proxy 만 alyak-pill-api:8000 으로 접속한다.
#  로그인은 직접 하지 않고, user-api 가 Redis 에 저장한 세션을 읽는다.
#  --env-file env/pill-api.env : DB 비밀번호
docker run -d --name alyak-pill-api --hostname alyak-pill-api --network alyak-net --restart unless-stopped --env-file env/pill-api.env junheong/alyak-pill-api:1.0
