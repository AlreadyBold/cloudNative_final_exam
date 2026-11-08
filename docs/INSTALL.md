# 설치 가이드

내약통한눈에를 내 PC(또는 서버)에 설치하고 실행하는 방법입니다.
모든 작업은 **Docker CLI 명령**으로 합니다 (Docker Compose를 쓰지 않습니다).
각 명령과 옵션의 의미는 [실행 명령어 전체](COMMANDS.md)에 정리되어 있습니다.

## 1. 준비물

| 항목 | 내용 |
|---|---|
| OS | Linux (Ubuntu 24.04에서 확인), macOS·Windows는 Docker Desktop |
| 프로그램 | Docker Engine 24 이상, git |
| 디스크 | 여유 약 4GB (이미지 약 3GB + DB 약 150MB) |
| 포트 | **8080**(서비스 입구), **33307**(DB 확인용)이 비어 있어야 함 |

```bash
docker --version   # Docker version 2x.x 이상인지 확인
```

## 2. 설치

어느 방법이든 먼저 저장소를 받습니다. 실행 스크립트와 비밀번호 파일(env) 생성 도구가 들어 있습니다.

```bash
git clone https://github.com/AlreadyBold/cloudNative_final_exam.git
cd cloudNative_final_exam
```

### 방법 A. Docker Hub에서 이미지를 받아 실행 (빠름)

```bash
./scripts/00_env.sh                         # ① 비밀번호 env 파일 생성 (무작위)
./scripts/01_network.sh                     # ② 네트워크 alyak-net 생성

# ③ 이미지 받기 (Docker Hub : junheong/<이름>:1.0)
docker pull junheong/alyak-mysql:1.0
docker pull junheong/alyak-user-api:1.0
docker pull junheong/alyak-pill-api:1.0
docker pull junheong/alyak-front:1.0
docker pull junheong/alyak-proxy:1.0
docker pull redis:8.10.3

./scripts/03_run.sh                         # ④ 컨테이너 7개 실행
```

### 방법 B. 소스에서 이미지를 빌드해 실행

PC의 CPU 종류(amd64 / arm64)에 상관없이 동작합니다. 빌드에 몇 분 걸립니다.
Java·Python·Gradle을 설치할 필요가 없습니다 (빌드가 이미지 안에서 이루어집니다).

```bash
./scripts/00_env.sh                         # ① 비밀번호 env 파일 생성 (무작위)
./scripts/01_network.sh                     # ② 네트워크 alyak-net 생성
./scripts/02_build.sh                       # ③ 이미지 5개 빌드
./scripts/03_run.sh                         # ④ 컨테이너 7개 실행
```

### 실행 확인

```bash
docker ps --filter name=alyak               # 컨테이너 7개가 Up 상태인지
```

처음 실행할 때 MySQL이 스키마와 약 카탈로그(약 4만 6천 건)를 넣느라 **15~30초** 걸립니다.
그동안 user-api가 DB를 기다리므로, 바로 접속하면 잠시 502가 보일 수 있습니다.

## 3. 접속

| 대상 | 주소 |
|---|---|
| 서비스 화면 | `http://<서버 IP>:8080` (같은 PC면 `http://localhost:8080`) |
| 약 서버 API 문서 (Swagger) | `http://<서버 IP>:8080/api/pill/docs` |
| DB (HeidiSQL 등, 확인용) | `<서버 IP>:33307`, 계정 `root`, 비밀번호는 `env/mysql.env`의 `MYSQL_ROOT_PASSWORD` |

다른 PC에서 접속이 안 되면 방화벽에서 8080을 열어 줍니다.

```bash
sudo ufw allow 8080/tcp
```

### 계정

| 계정 | 방법 |
|---|---|
| 일반 사용자 | 로그인 화면의 [회원가입하기] |
| 관리자 | 이메일 `admin@admin.com`, 비밀번호는 `env/user-api.env`의 `ADMIN_PASSWORD` (user-api가 처음 뜰 때 자동 생성) |

```bash
grep ADMIN_PASSWORD env/user-api.env        # 관리자 비밀번호 확인
```

관리자로 로그인하면 사용자 화면 대신 **백오피스**(대시보드 · 회원 관리 · 세션 확인)가 나옵니다.

## 4. (선택) 실제 처방내역 불러오기 : CODEF 키 설정

CODEF 키가 없으면 처방내역 불러오기는 **샘플(MOCK) 모드**로 동작합니다 (휴대폰 인증 없이 샘플 처방 3건).
실제 조회를 하려면 [CODEF](https://codef.io)에서 받은 키를 `env/user-api.env`에 채웁니다.

```
CODEF_SERVICE_TYPE=DEMO
CODEF_CLIENT_ID=<클라이언트 ID>
CODEF_CLIENT_SECRET=<클라이언트 시크릿>
CODEF_PUBLIC_KEY=<공개키>
```

env 파일은 **컨테이너를 만들 때만** 읽히므로 user-api 2대를 다시 만듭니다 (`docker restart`로는 반영되지 않음).

```bash
docker rm -f alyak-user-api-1 alyak-user-api-2
docker run -d --name alyak-user-api-1 --hostname alyak-user-api-1 --network alyak-net --restart unless-stopped --env-file env/user-api.env junheong/alyak-user-api:1.0
docker run -d --name alyak-user-api-2 --hostname alyak-user-api-2 --network alyak-net --restart unless-stopped --env-file env/user-api.env junheong/alyak-user-api:1.0
docker logs alyak-user-api-1 | grep CODEF   # "CODEF 연동 모드: DEMO" 가 보이면 성공
```

## 5. 데이터 보관 위치

| 데이터 | 위치 | 비고 |
|---|---|---|
| DB (회원, 내 약통, 약 카탈로그) | 호스트 `~/alyak/mysql-data` | 바인드 마운트. 컨테이너를 지우고 다시 만들어도 유지 |
| 로그인 세션 | Redis 컨테이너 메모리 | 30분 동안 요청이 없으면 만료 |
| 비밀번호 | 저장소의 `env/*.env` | git에 올라가지 않음 |

## 6. 문제 해결

| 증상 | 원인과 해결 |
|---|---|
| `port is already allocated` | 8080 또는 33307을 다른 프로그램이 사용 중. 그 프로그램을 끄거나, `03_run.sh`의 `-p` 앞 번호를 바꿈 |
| `network with name alyak-net already exists` | 이미 만들어져 있음. 그대로 다음 단계 진행 |
| `env/... : no such file` | `./scripts/00_env.sh`를 먼저 실행 |
| `exec format error` | 받은 이미지가 PC의 CPU(amd64/arm64)와 다름 → 방법 B(소스 빌드)로 설치 |
| 접속하면 502 | 서버가 아직 뜨는 중 (첫 실행 15~30초). `docker logs alyak-user-api-1`에 `Started` 가 나오면 다시 접속 |
| user-api 로그에 DB 접속 실패 (`Access denied`) | env 파일을 새로 만들어 비밀번호가 바뀌었는데 DB 폴더는 예전 것. 아래 "처음부터 다시"로 둘 다 지움 |

### 처음부터 다시 설치 (데이터 삭제)

```bash
docker rm -f alyak-proxy alyak-front alyak-pill-api alyak-user-api-1 alyak-user-api-2 alyak-redis alyak-mysql
docker network rm alyak-net
sudo rm -rf ~/alyak/mysql-data    # DB 데이터 삭제 (MySQL이 만든 파일이라 sudo 필요)
rm -f env/mysql.env env/user-api.env env/pill-api.env
```

그다음 `2. 설치`를 다시 진행합니다.
