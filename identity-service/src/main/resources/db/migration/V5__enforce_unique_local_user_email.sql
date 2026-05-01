-- LOCAL email/password 로그인은 email + account_type = LOCAL 이 단일 계정임을 전제로 한다.
-- MySQL 은 partial unique index 를 지원하지 않으므로 generated column 으로 LOCAL row 만 unique 대상에 포함한다.
-- OAUTH row 는 local_email = NULL 이라 provider 별 동일 email 공존 정책(자동 링크 없음)을 침범하지 않는다.

ALTER TABLE users
    ADD COLUMN local_email VARCHAR(254)
        GENERATED ALWAYS AS (
            CASE WHEN account_type = 'LOCAL' THEN email ELSE NULL END
        ) STORED,
    ADD CONSTRAINT chk_users_local_email_present CHECK (
        account_type <> 'LOCAL' OR email IS NOT NULL
    ),
    ADD UNIQUE KEY uk_users_local_email (local_email);
