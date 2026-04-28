-- 로컬 dev 전용. 한 서비스 = 한 스키마 = 한 user (Database per Service 정신, CLAUDE.md).
-- 서비스가 실 코드를 갖게 되면 동일 패턴으로 user/grant 만 활성화하면 된다.

CREATE DATABASE IF NOT EXISTS identity CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS diary    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS chat     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS learning CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS platform CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- identity-service
CREATE USER IF NOT EXISTS 'identity'@'%' IDENTIFIED BY 'identity-dev';
GRANT ALL PRIVILEGES ON identity.* TO 'identity'@'%';

-- diary-service
CREATE USER IF NOT EXISTS 'diary'@'%' IDENTIFIED BY 'diary-dev';
GRANT ALL PRIVILEGES ON diary.* TO 'diary'@'%';

-- chat-service
CREATE USER IF NOT EXISTS 'chat'@'%' IDENTIFIED BY 'chat-dev';
GRANT ALL PRIVILEGES ON chat.* TO 'chat'@'%';

-- learning-service
CREATE USER IF NOT EXISTS 'learning'@'%' IDENTIFIED BY 'learning-dev';
GRANT ALL PRIVILEGES ON learning.* TO 'learning'@'%';

-- platform-service
CREATE USER IF NOT EXISTS 'platform'@'%' IDENTIFIED BY 'platform-dev';
GRANT ALL PRIVILEGES ON platform.* TO 'platform'@'%';

FLUSH PRIVILEGES;
