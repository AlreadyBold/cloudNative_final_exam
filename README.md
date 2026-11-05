# 내약통한눈에 - 클라우드네이티브 기말 대체 과제


## 서비스 소개
이메일로 가입·로그인한 사용자가 간편인증(CODEF)으로 심평원 처방(조제) 내역을 불러오고,
원하는 약을 "내 약통" 또는 "내 처방내역"에 저장해 관리하는 웹 서비스입니다.

## 컨테이너 구성 (네트워크 `alyak-net`)
| 컨테이너 | 역할 |
|---|---|
| alyak-proxy | 역방향 프록시 (nginx) |
| alyak-front | 정적 HTML/CSS/JS 프론트 (nginx) |
| alyak-user-api-1, alyak-user-api-2 | 회원·마이페이지·처방내역 불러오기 (Spring Boot, 2대) |
| alyak-pill-api | 약 카탈로그·내 약통·저장한 처방내역 (FastAPI) |
| alyak-redis | 세션 저장소 (Redis) |
| alyak-mysql | DB (MySQL 8.4, 바인드 마운트) |

자세한 내용은 `docs/` 폴더를 참고하세요.
