-- 일기 본문 단일 content → 3줄 line1/line2/line3 재설계 (PRD 0526_flutter.md §2.3 "3줄 일기").
--
-- 박제: decisions/diary/diary-domain-policy.md §3 (content → lines), DiaryLines VO (정확히 3줄, 각 1..200cp).
-- 사용자 결정: 3컬럼 (JSON 아님), dev 데이터 초기화 가능.
--
-- VARCHAR(800) = 200 code points × 4 bytes (utf8mb4 surrogate 최악) — DiaryLines.LINE_MAX_CODE_POINTS(200) 정합.
--
-- dev 데이터 초기화 전제 — 기존 content row 는 line1/2/3 NOT NULL DEFAULT 로 채운 뒤 DEFAULT 제거.
-- (운영 데이터 마이그레이션이 필요했다면 content 를 줄 단위 split 하는 변환 스크립트가 필요하나, 본 시점
--  prod 미배포 + dev 초기화 합의로 단순 스키마 교체.)

ALTER TABLE diaries
    ADD COLUMN line1 VARCHAR(800) NOT NULL DEFAULT '' AFTER author_id,
    ADD COLUMN line2 VARCHAR(800) NOT NULL DEFAULT '' AFTER line1,
    ADD COLUMN line3 VARCHAR(800) NOT NULL DEFAULT '' AFTER line2;

-- 기존 content 컬럼 제거 (dev 초기화 — 데이터 보존 안 함).
ALTER TABLE diaries DROP COLUMN content;

-- 빈 문자열 DEFAULT 는 신규 row 가 항상 명시 값을 넣도록 제거 (DiaryLines invariant 가 blank 차단).
ALTER TABLE diaries
    ALTER COLUMN line1 DROP DEFAULT,
    ALTER COLUMN line2 DROP DEFAULT,
    ALTER COLUMN line3 DROP DEFAULT;
