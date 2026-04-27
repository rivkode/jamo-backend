-- LOCAL 회원가입 (PRD user/createUser.md §9, decisions/identity/local-credential-modeling.md).
-- 기존 사용자 (모두 OAuth 가입자) 는 account_type='OAUTH' 로 backfill, password_hash 는 NULL 유지.
--
-- 보안 정책 (PR6-b security review H1):
--   1. DEFAULT 'OAUTH' 잔존 금지 — INSERT 시 account_type 누락이 silent OAUTH 로 변환되는 것을 차단.
--   2. CHECK constraint 로 account_type ∈ {LOCAL, OAUTH}, LOCAL ↔ password_hash invariant 강제.
-- password_hash VARCHAR(255) — BCrypt 60자 + 향후 Argon2id 등 알고리즘 교체 대비 (security review L1).

-- 컬럼 추가 (NULL 허용)
ALTER TABLE users
    ADD COLUMN account_type VARCHAR(16) NULL AFTER email,
    ADD COLUMN password_hash VARCHAR(255) NULL AFTER account_type;

-- 기존 row backfill (PRD §9 가정: 기존 모든 사용자 OAuth)
UPDATE users SET account_type = 'OAUTH' WHERE account_type IS NULL;

-- NOT NULL 강제 + DEFAULT 미부여 (silent fallback 차단)
ALTER TABLE users MODIFY COLUMN account_type VARCHAR(16) NOT NULL;

-- 도메인 invariant 를 DB 레벨 안전망으로 박제
ALTER TABLE users
    ADD CONSTRAINT chk_users_account_type CHECK (account_type IN ('LOCAL', 'OAUTH')),
    ADD CONSTRAINT chk_users_local_password CHECK (
        (account_type = 'LOCAL' AND password_hash IS NOT NULL)
        OR (account_type = 'OAUTH' AND password_hash IS NULL)
    );

-- LOCAL 중복 검사 (existsLocalAccountByEmail) 의 핵심 인덱스.
-- (email, account_type) 복합 인덱스로 LOCAL 한정 lookup 최적화 — leftmost-prefix 매칭으로
-- 단순 email lookup 도 커버하므로 기존 idx_users_email 은 redundant 제거 (review M2).
CREATE INDEX idx_users_email_account_type ON users (email, account_type);
DROP INDEX idx_users_email ON users;
