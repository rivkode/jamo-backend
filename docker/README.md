# 로컬 docker compose

PR 머지 후 / E2E 검증용 로컬 환경. 운영 환경 아님.

## 구성

| 컨테이너 | 이미지 | 포트 | 비고 |
|---|---|---|---|
| `jamo-mysql` | mysql:8.0 | 3306 | 5개 스키마 자동 생성 (`identity / diary / chat / learning / platform`) |
| `jamo-redis` | redis:7-alpine | 6379 | requirepass + appendonly |
| `jamo-identity-service` | (build) | 8081 | 멀티스테이지 빌드, JRE 21 runtime |

서비스가 실 코드를 갖기 전까지 컨테이너는 추가하지 않는다 (스키마/유저만 미리 준비됨).

## 최초 실행

```bash
# 1) 환경변수 템플릿 복사
cp .env.example .env

# 2) 로컬 dev RSA 키쌍 + pepper 생성 → .env 끝에 붙여넣기
./docker/scripts/generate-dev-keys.sh >> .env

# 3) 기동
docker compose up -d

# 4) 헬스체크
docker compose ps
curl -fsS http://localhost:8081/actuator/health
```

## 자주 쓰는 명령

```bash
# 인프라만 (IDE 에서 :identity-service:bootRun 으로 띄울 때)
docker compose up -d mysql redis

# 로그
docker compose logs -f identity-service

# identity-service 재빌드 (Java 코드 변경 후)
docker compose build identity-service && docker compose up -d identity-service

# 완전 초기화 (DB 데이터까지 삭제)
docker compose down -v
```

## MySQL 직접 접속

```bash
# root
docker exec -it jamo-mysql mysql -uroot -proot

# identity-service user
docker exec -it jamo-mysql mysql -uidentity -pidentity-dev identity
```

## Redis 직접 접속

```bash
docker exec -it jamo-redis redis-cli -a jamo-redis-dev
```

## 새 서비스 추가 시 (CLAUDE.md "작업 규칙")

새 Java 서비스가 placeholder 를 벗어나 실 코드를 갖게 되는 PR 에서 다음을 **같은 PR 에 함께 포함**한다 (CLAUDE.md NEVER 위반).

1. **`<service>/Dockerfile`** — `identity-service/Dockerfile` 그대로 복사 후
   - `:identity-service:bootJar` → `:<service>:bootJar`
   - `identity-service-*.jar` → `<service>-*.jar`
   - `EXPOSE 8081` → 해당 서비스 포트
   - `COPY identity-service ...` → `COPY <service> ...`
2. **`docker-compose.yml`** — `identity-service:` 블록 복사 후 환경변수 prefix / 포트 / DB user 만 변경.
3. **`docker/mysql/init/01-create-schemas.sql`** — 이미 5개 스키마/유저가 준비되어 있으므로 보통 수정 불필요. 새 도메인 추가 시만 갱신.
4. **`.env.example`** — `<SERVICE>_DB_USERNAME / PASSWORD / SERVER_PORT` 추가.

## 트러블슈팅

| 증상 | 원인 / 조치 |
|---|---|
| `identity-service` 가 `JWT key material is missing` 로 죽음 | `.env` 의 `JWT_PRIVATE_KEY_PEM / PUBLIC / PEPPER` 미설정. `generate-dev-keys.sh` 출력을 .env 에 추가 |
| MySQL 컨테이너 healthy 안 됨 | `MYSQL_ROOT_PASSWORD` 변경 후 volume 미삭제. `docker compose down -v` 후 재기동 |
| Flyway migration 실패 | 스키마가 이미 다른 형태로 존재. `down -v` 로 초기화 |
