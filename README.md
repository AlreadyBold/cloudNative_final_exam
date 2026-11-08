# 내약통한눈에 - 클라우드네이티브 기말 대체 과제

먹는 약과 복용 기간을 웹에서 관리하는 **"웹 약통"** 서비스입니다.
약을 이름으로 검색해 담거나, 간편인증(CODEF)으로 심평원 처방(조제) 내역을 불러와 한 번에 담으면,
오늘 날짜를 기준으로 **복용 예정 · 복용 중 · 복용 완료**를 보여 줍니다.

- 컨테이너 7개를 **Docker CLI 명령만으로** 구성했습니다 (Docker Compose 미사용).
- 서버는 **Spring Boot 2대 + FastAPI 1대**의 이기종 마이크로서비스이며, 로그인 세션을 **Redis로 공유**합니다.

## 주요 기능

| 구분 | 기능 |
|---|---|
| 회원 | 이메일 회원가입·로그인, **계정당 로그인 1개** (다른 기기에서 로그인하면 이전 기기는 5초 안에 자동 로그아웃) |
| 약 검색 | 전문·일반의약품 46,292건 이름 검색, 상세 정보(성분·성상·**DUR 주의 정보**: 임부금기·연령금기·노인주의·병용금기) |
| 내 약통 | 복용 시작일·종료일 지정, 상태(복용 예정·복용 중·복용 완료) 자동 계산, 상태별 필터, 기간·메모 수정 |
| 처방내역 불러오기 | 카카오·네이버·토스 간편인증으로 최근 1년 조제 내역 조회 → 약통에 바로 담기 (기간 = 조제일 + 가장 긴 투약일수) |
| 마이페이지 | 닉네임·비밀번호 변경, 회원 탈퇴 |
| 관리자 | 컨테이너 상태 대시보드, 회원 관리(비밀번호 초기화), **세션 공유 확인** 화면 |

- 개인정보 보호 : 주민등록번호는 서버에 저장하지 않고, 화면에서도 뒷자리·휴대폰 번호를 가립니다.
- CODEF 키가 없으면 처방내역 불러오기가 **샘플(MOCK) 모드**로 동작해서, 키 없이도 모든 기능을 확인할 수 있습니다.

## 시스템 구성 (네트워크 `alyak-net`)

```
브라우저 ──▶ :8080 alyak-proxy (nginx)
                 ├─ /             ──▶ alyak-front        (nginx, 순수 HTML/CSS/JS)
                 ├─ /api/user/**  ──▶ alyak-user-api-1   (Spring Boot)  ─┐
                 │                    alyak-user-api-2   (Spring Boot)  ─┼─▶ alyak-redis (세션 공유)
                 └─ /api/pill/**  ──▶ alyak-pill-api     (FastAPI)      ─┘
                                        │ (user-api: alyak_user 스키마 / pill-api: alyak_pill 스키마)
                                        ▼
                                     alyak-mysql (MySQL 8.4, ~/alyak/mysql-data 바인드 마운트)
```

| 컨테이너 | 이미지 | 역할 | 포트 (호스트 → 컨테이너) |
|---|---|---|---|
| alyak-proxy | `junheong/alyak-proxy:1.0` | 역방향 프록시. 경로별로 서버에 분배, user-api 2대는 번갈아 분배 | **8080 → 80** |
| alyak-front | `junheong/alyak-front:1.0` | 화면 (빌드 도구 없는 HTML/CSS/JS) | 열지 않음 (80) |
| alyak-user-api-1, -2 | `junheong/alyak-user-api:1.0` | 회원·로그인·마이페이지·처방내역 불러오기·관리자 API | 열지 않음 (8080) |
| alyak-pill-api | `junheong/alyak-pill-api:1.0` | 약 검색·상세, 내 약통 | 열지 않음 (8000) |
| alyak-redis | `redis:8.10.3` (공식) | 로그인 세션 저장소 | 열지 않음 (6379) |
| alyak-mysql | `junheong/alyak-mysql:1.0` | DB (스키마·계정·약 카탈로그 초기화 데이터 내장) | 33307 → 3306 (확인용) |

브라우저는 프록시 한 곳(같은 주소)으로만 접속하므로 `SESSION` 쿠키가 세 서버에 똑같이 전달되고,
세 서버 모두 Redis의 같은 세션을 읽어 로그인 사용자를 확인합니다.

## 빠른 시작

```bash
git clone https://github.com/AlreadyBold/cloudNative_final_exam.git
cd cloudNative_final_exam
./scripts/00_env.sh       # 비밀번호 env 파일 생성 (무작위, git에 올라가지 않음)
./scripts/01_network.sh   # 네트워크 alyak-net 생성
./scripts/02_build.sh     # 이미지 5개 빌드 (Docker Hub에서 받을 경우 생략 가능)
./scripts/03_run.sh       # 컨테이너 7개 실행
```

브라우저에서 `http://<서버 IP>:8080` 으로 접속합니다. 관리자 계정은 `admin@admin.com`이고,
비밀번호는 `env/user-api.env`의 `ADMIN_PASSWORD`에 있습니다.

## 문서

- [설치 가이드](docs/INSTALL.md) : 준비물, Docker Hub에서 받아 설치 / 소스에서 빌드, 접속 방법, 문제 해결
- [실행 명령어 전체](docs/COMMANDS.md) : 실행한 모든 `docker build`, `docker run` 명령을 순서대로, 검증 명령 포함

## 기술 스택

| 영역 | 사용 기술 |
|---|---|
| 프론트 | HTML, CSS, JavaScript (ES 모듈, Fetch API), nginx 1.31.8 |
| 회원 서버 | Java 17, Spring Boot 3.5.6, Spring Session (Redis), Spring Data JPA, CODEF 공식 라이브러리 |
| 약 서버 | Python 3.12, FastAPI, SQLAlchemy, redis-py |
| 저장소 | MySQL 8.4, Redis 8.10.3 |
| 인프라 | Docker Engine (CLI), 사용자 정의 브리지 네트워크, 바인드 마운트 |

## 데이터 출처

약 정보는 식품의약품안전처 의약품 낱알식별·허가 정보, 건강보험심사평가원 DUR(의약품안전사용서비스)·약가마스터
공공데이터를 가공해 DB 초기화 데이터(`db/initdb/03_insert_pill_catalog.sql.gz`)로 넣었습니다.
변환 도구는 `tools/build_catalog_sql.py` 입니다.
