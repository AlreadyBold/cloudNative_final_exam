# 실행 명령어 전체

이 프로젝트를 만들고 실행할 때 쓴 **모든 Docker 명령을 실행 순서대로** 적었습니다.
Docker Compose를 쓰지 않고 Docker CLI 명령만 사용합니다.
`scripts/` 폴더의 스크립트는 아래 명령을 그대로 묶어 둔 것이라, 명령을 한 줄씩 직접 입력해도 결과가 같습니다.

모든 명령은 **저장소 최상위 폴더**(`cloudNative_final_exam/`)에서 실행합니다.

| 순서 | 내용 | 스크립트 |
|---|---|---|
| 0 | 비밀번호 env 파일 만들기 | `scripts/00_env.sh` |
| 1 | 네트워크 만들기 | `scripts/01_network.sh` |
| 2 | 이미지 빌드 (또는 Docker Hub에서 받기) | `scripts/02_build.sh` |
| 3 | 컨테이너 실행 | `scripts/03_run.sh` |
| 4 | 동작 검증 | (아래 명령) |

---

## 0. 비밀번호 env 파일 만들기

```bash
./scripts/00_env.sh
```

DB 비밀번호는 코드·이미지·git에 넣지 않고, 이 스크립트가 무작위로 만든 env 파일에만 둡니다.
각 컨테이너는 `docker run --env-file`로 **자기에게 필요한 값만** 받습니다.

| 파일 | 받는 컨테이너 | 내용 |
|---|---|---|
| `env/mysql.env` | alyak-mysql | `MYSQL_ROOT_PASSWORD`, `USER_DB_PASSWORD`, `PILL_DB_PASSWORD` (최초 초기화 때 계정 생성) |
| `env/user-api.env` | alyak-user-api-1, -2 | `DB_PASSWORD`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `CODEF_*`(선택) |
| `env/pill-api.env` | alyak-pill-api | `DB_PASSWORD` |

## 1. 네트워크 만들기

```bash
docker network create --driver bridge alyak-net
```

| 옵션 | 의미 |
|---|---|
| `--driver bridge` | 한 호스트 안에서 컨테이너끼리 통신하는 브리지 네트워크 |
| `alyak-net` | 네트워크 이름. **사용자 정의 네트워크**라서 도커 내장 DNS(127.0.0.11)가 컨테이너 이름을 IP로 바꿔 줌 → 설정에 IP 대신 `alyak-mysql` 같은 이름을 씀 |

## 2. 이미지 빌드

```bash
docker build -f db/Dockerfile.mysql -t junheong/alyak-mysql:1.0 db
docker build -f user-api/Dockerfile.user-api -t junheong/alyak-user-api:1.0 user-api
docker build -f pill-api/Dockerfile.pill-api -t junheong/alyak-pill-api:1.0 pill-api
docker build -f front/Dockerfile.front -t junheong/alyak-front:1.0 front
docker build -f proxy/Dockerfile.proxy -t junheong/alyak-proxy:1.0 proxy
docker builder prune -f
```

| 옵션 | 의미 |
|---|---|
| `-f <경로>` | 사용할 Dockerfile |
| `-t junheong/<이름>:1.0` | 이미지 이름 (Docker Hub 아이디/저장소:태그) |
| 마지막 인자 (`db`, `user-api` …) | 빌드 컨텍스트 = `COPY` 할 파일이 있는 폴더 |
| `docker builder prune -f` | 빌드 중간 캐시 정리 (이미지·컨테이너는 지워지지 않음) |

| 이미지 | 기반 이미지 | 이미지에 넣는 것 (`COPY`) |
|---|---|---|
| alyak-mysql | `mysql:8.4` | 설정 파일, 초기화 스크립트 3개 (스키마·테이블 / 계정 생성 / 약 카탈로그 데이터) |
| alyak-user-api | 빌드 `gradle:8.14.3-jdk17-noble` → 실행 `eclipse-temurin:17-jre-noble` | 멀티 스테이지 : 소스를 컴파일한 jar 하나만 실행 이미지에 복사 |
| alyak-pill-api | `python:3.12.7-slim-bookworm` | `requirements.txt` 설치 + 소스 |
| alyak-front | `nginx:1.31.8` | HTML/CSS/JS 파일, nginx 설정 |
| alyak-proxy | `nginx:1.31.8` | 역방향 프록시 설정 |

소스는 바인드 마운트가 아니라 **이미지 안에 복사**하므로, 이미지만 받아도 같은 프로그램이 실행됩니다.
Redis는 공식 이미지 `redis:8.10.3`을 그대로 씁니다.

### (빌드 대신) Docker Hub에서 받기

```bash
docker pull junheong/alyak-mysql:1.0
docker pull junheong/alyak-user-api:1.0
docker pull junheong/alyak-pill-api:1.0
docker pull junheong/alyak-front:1.0
docker pull junheong/alyak-proxy:1.0
docker pull redis:8.10.3
```

### Docker Hub에 올리기 (개발자가 실행)

```bash
docker login
docker push junheong/alyak-mysql:1.0
docker push junheong/alyak-user-api:1.0
docker push junheong/alyak-pill-api:1.0
docker push junheong/alyak-front:1.0
docker push junheong/alyak-proxy:1.0
```

## 3. 컨테이너 실행

**의존 관계 순서**대로 실행합니다 : DB·세션 저장소 → 서버 → 프론트 → 프록시.

```bash
mkdir -p ~/alyak/mysql-data
docker run -d --name alyak-mysql --hostname alyak-mysql --network alyak-net --restart unless-stopped -p 33307:3306 --env-file env/mysql.env -v ~/alyak/mysql-data:/var/lib/mysql junheong/alyak-mysql:1.0
docker run -d --name alyak-redis --hostname alyak-redis --network alyak-net --restart unless-stopped redis:8.10.3
docker run -d --name alyak-user-api-1 --hostname alyak-user-api-1 --network alyak-net --restart unless-stopped --env-file env/user-api.env junheong/alyak-user-api:1.0
docker run -d --name alyak-user-api-2 --hostname alyak-user-api-2 --network alyak-net --restart unless-stopped --env-file env/user-api.env junheong/alyak-user-api:1.0
docker run -d --name alyak-pill-api --hostname alyak-pill-api --network alyak-net --restart unless-stopped --env-file env/pill-api.env junheong/alyak-pill-api:1.0
docker run -d --name alyak-front --hostname alyak-front --network alyak-net --restart unless-stopped junheong/alyak-front:1.0
docker run -d --name alyak-proxy --hostname alyak-proxy --network alyak-net --restart unless-stopped -p 8080:80 junheong/alyak-proxy:1.0
```

### 공통 옵션

| 옵션 | 의미 |
|---|---|
| `-d` | 백그라운드(detached)로 실행 |
| `--name <이름>` | 컨테이너 이름. 같은 네트워크 안에서는 이 이름이 곧 접속 주소 |
| `--hostname <이름>` | 컨테이너 안의 hostname. 서버 응답의 `served_by` / `X-Served-By` 헤더에 찍혀서 **어느 컨테이너가 처리했는지** 확인할 수 있음 |
| `--network alyak-net` | 1단계에서 만든 네트워크에 연결 |
| `--restart unless-stopped` | 재부팅·오류 종료 시 자동 재시작 (직접 stop 한 경우는 제외) |
| `--env-file env/<파일>` | 비밀번호 등 환경변수를 파일에서 읽어 전달 (0단계에서 만든 파일) |

### 컨테이너별 옵션

| 컨테이너 | 옵션 | 의미 |
|---|---|---|
| alyak-mysql | `-p 33307:3306` | 호스트 33307 → 컨테이너 3306. DB를 HeidiSQL 등으로 확인하기 위한 용도 |
| | `-v ~/alyak/mysql-data:/var/lib/mysql` | **DB 데이터 폴더를 호스트에 바인드 마운트** → 컨테이너를 지우고 다시 만들어도 데이터 유지 (데이터 폴더가 비어 있을 때만 초기화 스크립트 실행) |
| alyak-redis | (포트 없음) | 서버들만 `alyak-redis:6379`로 접속. 외부에 열지 않음 |
| alyak-user-api-1, -2 | 같은 이미지 2개, `--hostname`만 다름 | 프록시가 번갈아 요청을 보냄. 세션을 Redis에 두므로 어느 쪽이 받아도 로그인 유지 |
| alyak-pill-api | (포트 없음) | 프록시만 `alyak-pill-api:8000`으로 접속 |
| alyak-front | (포트 없음) | 프록시만 `alyak-front:80`으로 접속 |
| alyak-proxy | `-p 8080:80` | **브라우저가 접속하는 유일한 입구.** 호스트 8080 → 컨테이너 80 |

프록시의 경로별 분배 (`proxy/nginx.conf`)

| 요청 경로 | 보내는 곳 |
|---|---|
| `/api/user/**` | alyak-user-api-1, alyak-user-api-2 (라운드 로빈) |
| `/api/pill/**` | alyak-pill-api |
| 그 밖 (`/`) | alyak-front |

## 4. 동작 검증

### 컨테이너·네트워크 확인

```bash
docker ps --filter name=alyak                 # 7개 모두 Up
docker network inspect alyak-net              # 7개 컨테이너가 같은 네트워크에 연결
docker exec -it alyak-user-api-1 bash         # 컨테이너 안으로 접속 (나올 때 exit)
```

`docker exec -it <컨테이너명> bash`는 7개 컨테이너 모두에서 동작합니다.

### 서버 분산 확인 (user-api 2대 번갈아 처리)

```bash
for i in 1 2 3 4; do curl -s -D - -o /dev/null http://localhost:8080/api/user/health | grep -i x-served-by; done
```

`X-Served-By: alyak-user-api-1`과 `alyak-user-api-2`가 번갈아 나옵니다.

### 세션 공유 확인

화면에서 확인 : 관리자로 로그인 → **세션 확인** 탭 → [세션 공유 확인]
user-api 1·2와 pill-api가 **같은 세션 ID, 같은 사용자**를 보여 주면 성공입니다.

명령으로 확인 :

```bash
# 회원가입 후 로그인해서 SESSION 쿠키 값을 받는다
curl -s -H 'Content-Type: application/json' -X POST http://localhost:8080/api/user/signup \
  -d '{"email":"test@test.com","password":"password123","nickname":"테스트"}'
S=$(curl -s -D - -o /dev/null -H 'Content-Type: application/json' -X POST http://localhost:8080/api/user/login \
  -d '{"email":"test@test.com","password":"password123"}' | sed -n 's/^Set-Cookie: SESSION=\([^;]*\);.*/\1/p')

# 같은 쿠키로 서버 3곳에 요청 → served_by 는 다르고 session_id_prefix, login_user 는 같다
for i in 1 2; do curl -s -H "Cookie: SESSION=$S" http://localhost:8080/api/user/me; echo; done
curl -s -H "Cookie: SESSION=$S" http://localhost:8080/api/pill/me; echo

# Redis 에 저장된 세션 (Spring 이 JSON 으로 저장 → 파이썬도 읽을 수 있음)
docker exec alyak-redis redis-cli HGETALL "alyak:session:sessions:$S"
```

### DB 볼륨 마운트·데이터 영속성 확인

```bash
# 1) 데이터가 호스트 폴더에 있는지
docker inspect alyak-mysql --format '{{range .Mounts}}{{.Type}} {{.Source}} -> {{.Destination}}{{end}}'
sudo ls ~/alyak/mysql-data

# 2) 화면에서 회원가입·약 담기를 한 뒤, DB 컨테이너를 지우고 같은 명령으로 다시 만든다
docker rm -f alyak-mysql
docker run -d --name alyak-mysql --hostname alyak-mysql --network alyak-net --restart unless-stopped -p 33307:3306 --env-file env/mysql.env -v ~/alyak/mysql-data:/var/lib/mysql junheong/alyak-mysql:1.0

# 3) 다시 로그인하면 계정과 내 약통이 그대로 있다 (또는 재시작만 해도 됨)
docker restart alyak-mysql
```

### 로그 보기

```bash
docker logs alyak-user-api-1          # Spring Boot 로그
docker logs alyak-pill-api            # FastAPI 로그
docker logs alyak-proxy               # 요청이 어느 서버로 갔는지
```

## 5. 정리 (모두 지우기)

```bash
docker rm -f alyak-proxy alyak-front alyak-pill-api alyak-user-api-1 alyak-user-api-2 alyak-redis alyak-mysql
docker network rm alyak-net
# 이미지까지 지우려면
docker rmi junheong/alyak-proxy:1.0 junheong/alyak-front:1.0 junheong/alyak-pill-api:1.0 junheong/alyak-user-api:1.0 junheong/alyak-mysql:1.0
# DB 데이터까지 지우려면 (다음 실행 때 초기화 스크립트가 다시 실행됨)
sudo rm -rf ~/alyak/mysql-data
```
