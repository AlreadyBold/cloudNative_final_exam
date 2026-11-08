-- =====================================================================
-- 내약통한눈에 DB 초기화 스크립트 (1/3) : 스키마 · 테이블 생성
--
-- [언제 실행되나]
--  mysql 공식 이미지는 /var/lib/mysql 이 "비어 있을 때" (= 최초 1회)
--  /docker-entrypoint-initdb.d/ 안의 *.sql, *.sh, *.sql.gz 파일을 이름 순서대로 실행한다.
--  바인드 마운트한 ~/alyak/mysql-data 에 데이터가 이미 있으면 다시 실행되지 않으므로
--  컨테이너를 지우고 다시 만들어도 기존 데이터가 그대로 유지된다 (영속성).
--
-- [서비스별 스키마 분리]
--  alyak_user : user-api(Spring Boot) 전용 - 회원 정보
--  alyak_pill : pill-api(FastAPI)     전용 - 약 카탈로그, 내 약통, 저장한 처방내역
--  같은 MySQL 컨테이너를 쓰지만 계정과 권한을 나눠서
--  각 서버는 자기 스키마에만 접근할 수 있다 (마이크로서비스의 "서비스별 DB" 원칙).
--  계정은 비밀번호를 환경변수로 받아야 하므로 02_create_accounts.sh 가 만든다.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. 스키마(데이터베이스) 생성
-- ---------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS alyak_user
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS alyak_pill
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


-- =====================================================================
-- 2. alyak_user 스키마 테이블 (user-api 가 사용)
-- =====================================================================

-- 회원 테이블
--  - 로그인 아이디는 이메일, 비밀번호는 BCrypt 해시로만 저장한다 (원문 저장 금지).
CREATE TABLE IF NOT EXISTS alyak_user.users (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '회원 번호 (세션의 userId 로 사용)',
  email         VARCHAR(100) NOT NULL                COMMENT '로그인 아이디 (세션의 username 으로 사용)',
  password_hash VARCHAR(100) NOT NULL                COMMENT 'BCrypt 비밀번호 해시',
  nickname      VARCHAR(20)  NOT NULL                COMMENT '화면 표시용 이름 (세션의 nickname)',
  created_at    DATETIME(6)  NOT NULL                COMMENT '가입 시각',
  updated_at    DATETIME(6)  NOT NULL                COMMENT '마지막 수정 시각',
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email)
) ENGINE = InnoDB COMMENT = '회원';


-- =====================================================================
-- 3. alyak_pill 스키마 테이블 (pill-api 가 사용)
--    ※ user_id 는 다른 서비스(alyak_user)의 회원 번호다.
--      서비스 간 DB 를 분리했으므로 외래키(FK)는 걸지 않고 값만 저장한다.
-- =====================================================================

-- 약 카탈로그 (식약처 낱알식별·허가정보 기반, 약 1개 = 1행)
--  - 식약처 원천 데이터에서 웹 화면에 필요한 컬럼만 남겼다.
--  - 데이터는 03_insert_pill_catalog.sql.gz 가 넣는다. (tools/build_catalog_sql.py 로 생성)
--  - 이미지 파일은 저장하지 않고 식약처 원본 이미지 주소(image_url)만 저장한다.
CREATE TABLE IF NOT EXISTS alyak_pill.pill_catalog (
  item_seq             VARCHAR(20)   NOT NULL COMMENT '품목기준코드 (약 고유 ID)',
  item_name            VARCHAR(500)  NOT NULL COMMENT '약품명',
  item_name_normalized VARCHAR(500)  NOT NULL COMMENT '검색용 약품명 (괄호·공백 제거, 소문자, 밀리그램→mg)',
  entp_name            VARCHAR(100)      NULL COMMENT '제조/수입사',
  etc_otc              VARCHAR(20)       NULL COMMENT '전문의약품 / 일반의약품',
  class_name           VARCHAR(100)      NULL COMMENT '분류명 (예: 해열.진통.소염제)',
  main_ingredient      TEXT              NULL COMMENT '주성분',
  drug_shape           VARCHAR(20)       NULL COMMENT '모양 (원형, 타원형 ...)',
  color1               VARCHAR(30)       NULL COMMENT '색상 (앞)',
  color2               VARCHAR(30)       NULL COMMENT '색상 (뒤)',
  print_front          VARCHAR(100)      NULL COMMENT '각인 (앞)',
  print_back           VARCHAR(100)      NULL COMMENT '각인 (뒤)',
  line_front           VARCHAR(10)       NULL COMMENT '분할선 (앞)',
  line_back            VARCHAR(10)       NULL COMMENT '분할선 (뒤)',
  form_code_name       VARCHAR(50)       NULL COMMENT '제형 (필름코팅정, 경질캡슐 ...)',
  chart                VARCHAR(500)      NULL COMMENT '성상 설명',
  leng_long            DECIMAL(6, 2)     NULL COMMENT '장축 크기(mm)',
  leng_short           DECIMAL(6, 2)     NULL COMMENT '단축 크기(mm)',
  thick                DECIMAL(6, 2)     NULL COMMENT '두께(mm)',
  image_url            VARCHAR(255)      NULL COMMENT '낱알 이미지 원본 주소 (식약처)',
  is_pill              TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '먹는 알약(정제·캡슐) 여부',
  sale_status          VARCHAR(20)   NOT NULL DEFAULT 'UNKNOWN' COMMENT '판매 상태 (NORMAL, EXPIRED ...)',
  PRIMARY KEY (item_seq),
  KEY idx_pill_catalog_name_normalized (item_name_normalized(100))
) ENGINE = InnoDB COMMENT = '약 카탈로그';

-- 약 코드 → 약 연결 (보험코드 EDI 등). 처방내역의 약품코드로 카탈로그를 찾을 때 사용
CREATE TABLE IF NOT EXISTS alyak_pill.pill_catalog_code (
  id        BIGINT      NOT NULL AUTO_INCREMENT,
  code_type VARCHAR(10) NOT NULL COMMENT '코드 종류 (EDI: 보험코드)',
  code      VARCHAR(20) NOT NULL COMMENT '코드 값 (보험코드 9자리 등)',
  item_seq  VARCHAR(20) NOT NULL COMMENT '연결된 약 (pill_catalog.item_seq)',
  PRIMARY KEY (id),
  UNIQUE KEY uk_pill_catalog_code (code_type, code, item_seq),
  KEY idx_pill_catalog_code_item (item_seq)
) ENGINE = InnoDB COMMENT = '약 코드 매핑';

-- 약별 DUR(의약품 안전사용 정보) : 약 상세 화면에 "주의 정보"로 표시
--  - 카탈로그 원천(jsonl)의 dur 항목에서 만든다. 약 1개에 여러 행.
--  - PREGNANCY 임부금기 / AGE 연령금기 / ELDERLY 노인주의 / COMBINATION 병용금기
CREATE TABLE IF NOT EXISTS alyak_pill.pill_catalog_dur (
  id                 BIGINT        NOT NULL AUTO_INCREMENT,
  item_seq           VARCHAR(20)   NOT NULL COMMENT '약 (pill_catalog.item_seq)',
  dur_type           VARCHAR(20)   NOT NULL COMMENT 'PREGNANCY / AGE / ELDERLY / COMBINATION',
  partner_ingredient VARCHAR(200)      NULL COMMENT '병용금기 : 같이 먹으면 안 되는 성분 (영문)',
  grade              VARCHAR(10)       NULL COMMENT '임부금기 등급 (1, 2)',
  age_condition      VARCHAR(20)       NULL COMMENT '연령금기 조건 (예: 12세 미만)',
  detail             VARCHAR(1000)     NULL COMMENT '상세 내용',
  PRIMARY KEY (id),
  KEY idx_pill_catalog_dur_item (item_seq, dur_type)
) ENGINE = InnoDB COMMENT = '약별 DUR 주의 정보';

-- DUR 병용금기 검사용 (1/2) : 보험코드(EDI) → 성분코드
--  - "내 약통"에 담긴 약끼리 함께 먹으면 안 되는 조합이 있는지 검사할 때 사용한다.
--  - 심평원 DUR 병용금기 품목리스트(CSV)의 제품코드/성분코드 열에서 만든다.
--  - 성분코드(9자리) = 성분 4자리 + 함량 2자리 + 투여경로 1자리 + 제형 2자리
CREATE TABLE IF NOT EXISTS alyak_pill.dur_ingredient (
  edi_code        VARCHAR(20)  NOT NULL COMMENT '보험코드 (제품코드)',
  ingredient_code VARCHAR(20)  NOT NULL COMMENT '성분코드',
  ingredient_name VARCHAR(500) NOT NULL COMMENT '성분명 (영문)',
  PRIMARY KEY (edi_code),
  KEY idx_dur_ingredient_code (ingredient_code)
) ENGINE = InnoDB COMMENT = 'DUR 보험코드-성분 매핑';

-- DUR 병용금기 검사용 (2/2) 성분 쌍 : "성분 A 와 성분 B 를 함께 먹으면 안 된다"
--  - 원본 CSV 는 제품 쌍 단위(약 87만 행)라서, 같은 성분 쌍을 하나로 합쳐 약 2만 4천 행으로 줄였다.
--  - 항상 code_a < code_b 로 저장해서 (A,B) 와 (B,A) 가 중복되지 않게 한다.
CREATE TABLE IF NOT EXISTS alyak_pill.dur_combination (
  ingredient_code_a VARCHAR(20)   NOT NULL COMMENT '성분코드 (작은 쪽)',
  ingredient_code_b VARCHAR(20)   NOT NULL COMMENT '성분코드 (큰 쪽)',
  detail            VARCHAR(1000)     NULL COMMENT '금기 사유 (예: 혈중농도 상승 및 근육병증 위험 증가)',
  PRIMARY KEY (ingredient_code_a, ingredient_code_b),
  KEY idx_dur_combination_b (ingredient_code_b)
) ENGINE = InnoDB COMMENT = 'DUR 병용금기 성분 쌍';

-- 내 약통 : 사용자가 "지금 먹고 있는 약"으로 담아 둔 약 목록
--  - 약 검색 결과에서 담거나(SEARCH), 불러온 처방내역에서 담을 수 있다(PRESCRIPTION).
--  - 카탈로그에 없는 약(처방내역에만 있는 약)도 담을 수 있도록 item_seq 는 NULL 허용.
CREATE TABLE IF NOT EXISTS alyak_pill.pillbox_items (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  user_id    BIGINT       NOT NULL COMMENT '회원 번호 (alyak_user.users.id, 세션에서 얻음)',
  item_seq   VARCHAR(20)      NULL COMMENT '카탈로그 약 ID (카탈로그에 없으면 NULL)',
  drug_code  VARCHAR(20)      NULL COMMENT '보험 약품코드 (처방내역에서 담은 경우)',
  drug_name  VARCHAR(500) NOT NULL COMMENT '약품명 (담은 시점 이름 그대로 보관)',
  memo       VARCHAR(200)     NULL COMMENT '사용자 메모 (예: 아침 식후 1알)',
  source     VARCHAR(20)  NOT NULL COMMENT '담은 경로 (SEARCH / PRESCRIPTION)',
  created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '담은 시각',
  PRIMARY KEY (id),
  KEY idx_pillbox_items_user (user_id)
) ENGINE = InnoDB COMMENT = '내 약통';

-- 저장한 처방내역 (조제 1건 = 1행)
--  - 간편인증으로 불러온 처방내역 중 사용자가 "저장"을 누른 것만 보관한다.
CREATE TABLE IF NOT EXISTS alyak_pill.saved_prescriptions (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  user_id        BIGINT       NOT NULL COMMENT '회원 번호 (alyak_user.users.id)',
  dispensed_date DATE             NULL COMMENT '조제일',
  pharmacy       VARCHAR(100)     NULL COMMENT '조제기관(약국)',
  prescriber     VARCHAR(100)     NULL COMMENT '처방기관(병원)',
  data_source    VARCHAR(10)  NOT NULL COMMENT '불러온 방식 (CODEF: 실제 조회, MOCK: 샘플 데이터)',
  created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '저장 시각',
  PRIMARY KEY (id),
  KEY idx_saved_prescriptions_user (user_id, dispensed_date)
) ENGINE = InnoDB COMMENT = '저장한 처방내역';

-- 저장한 처방내역에 들어 있는 약 (처방 1건에 약 여러 개)
CREATE TABLE IF NOT EXISTS alyak_pill.saved_prescription_drugs (
  id                BIGINT        NOT NULL AUTO_INCREMENT,
  prescription_id   BIGINT        NOT NULL COMMENT '소속 처방 (saved_prescriptions.id)',
  drug_code         VARCHAR(20)       NULL COMMENT '보험 약품코드',
  drug_name         VARCHAR(500)  NOT NULL COMMENT '약품명',
  ingredients       VARCHAR(1000)     NULL COMMENT '성분명',
  one_dose          VARCHAR(20)       NULL COMMENT '1회 투약량',
  daily_doses       VARCHAR(20)       NULL COMMENT '1일 투여횟수',
  total_dosing_days VARCHAR(20)       NULL COMMENT '총 투약일수',
  effect            VARCHAR(200)      NULL COMMENT '약효',
  formula           VARCHAR(500)      NULL COMMENT '성상 (예: 흰색의 원형 정제)',
  item_seq          VARCHAR(20)       NULL COMMENT '보험코드로 찾은 카탈로그 약 ID (없으면 NULL)',
  PRIMARY KEY (id),
  KEY idx_saved_prescription_drugs_prescription (prescription_id),
  -- 처방 1건을 지우면 그 안의 약도 같이 지워진다 (같은 서비스 안의 테이블이므로 FK 사용)
  CONSTRAINT fk_saved_prescription_drugs_prescription
    FOREIGN KEY (prescription_id) REFERENCES alyak_pill.saved_prescriptions (id) ON DELETE CASCADE
) ENGINE = InnoDB COMMENT = '저장한 처방내역의 약';
