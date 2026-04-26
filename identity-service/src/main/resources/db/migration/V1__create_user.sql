CREATE TABLE users (
    id BINARY(16) NOT NULL,
    display_name VARCHAR(32) NOT NULL,
    email VARCHAR(254) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_users_email (email)
) ENGINE=InnoDB CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
