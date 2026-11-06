#!/bin/bash
# =====================================================================
# 02_build.sh : 내약통한눈에 커스텀 이미지 빌드
#
#  - 저장소 최상위 폴더에서 실행한다고 가정하고, 스크립트 위치 기준으로 이동한 뒤 빌드한다.
#  - 소스는 모두 Dockerfile 의 COPY 로 이미지 안에 들어간다. (바인드 마운트에 의존하지 않음)
#  - 이미지 이름 규칙 : junheong/<컨테이너 이름>:1.0
#  - redis 는 공식 이미지를 그대로 쓰므로 빌드하지 않는다.
# =====================================================================
set -e
cd "$(dirname "$0")/.."   # scripts/ 의 상위 = 저장소 최상위 폴더로 이동

# [1] DB 이미지 : mysql:8.4 + 설정 파일 + 초기화 SQL(스키마·계정·테이블·카탈로그)
#  -f  : 사용할 Dockerfile 경로
#  -t  : 만들 이미지 이름:태그
#  db  : 빌드 컨텍스트 (COPY 할 파일들이 있는 폴더)
docker build -f db/Dockerfile.mysql -t junheong/alyak-mysql:1.0 db

# [2] 회원 서버 이미지 : Spring Boot (멀티 스테이지 - gradle 로 jar 빌드 → JRE 이미지에 jar 만 복사)
#  VM 에 Java/Gradle 을 설치할 필요 없이 이미지 빌드 과정에서 컴파일까지 끝난다
docker build -f user-api/Dockerfile.user-api -t junheong/alyak-user-api:1.0 user-api

# [3] 약 서버 이미지 : FastAPI (python slim + requirements.txt 설치 + 소스 COPY)
docker build -f pill-api/Dockerfile.pill-api -t junheong/alyak-pill-api:1.0 pill-api

# 빌드 중간 캐시 정리 (VM 디스크 용량이 작아서 매번 비운다. 이미지와 컨테이너는 지워지지 않음)
docker builder prune -f
