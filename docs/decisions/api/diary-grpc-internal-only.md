# DiaryQueryService gRPC — 내부 전용 + prod 배포 BLOCK 조건

- **상태**: Accepted (dev 한정) / prod 진입 전 BLOCK 조건 미충족
- **날짜**: 2026-05-31
- **영향 범위**: diary-service (gRPC server), identity-service (gRPC client)
- **관련**: Slice 3-b (Profile diaryCount), contracts/diary.proto, security-reviewer C1/H1/H2

## 컨텍스트

Slice 3-b 에서 diary-service 가 처음으로 gRPC server (`DiaryQueryService.GetDiaryCount`) 를 노출하고,
identity-service 가 client 로 호출해 프로필의 diaryCount 를 합성한다.

`GetDiaryCountRequest.include_private` 플래그는 **호출자(identity)가 viewer==target 으로 판별**해
설정한다 (본인=true 전체 / 타인=false 공개만, IDOR 차단). 즉 비공개 일기 수 누설 차단의 신뢰 경계가
**호출자 측**에 있다.

현 시점 gRPC server 에는 **인증 / authz interceptor 가 없다**. diary-service 는 `include_private` 를
그대로 신뢰한다. 따라서 gRPC 포트가 신뢰 경계 밖(host / 외부망)에 노출되면 application-layer IDOR
방어가 무력화된다 — 임의 호출자가 `include_private=true` 로 임의 사용자의 PRIVATE 포함 전체 일기 수를
조회 가능 (security-reviewer C1).

## 결정

### 1. dev/local: host 포트 바인딩 금지

`docker-compose.yml` 에서 diary-service 의 gRPC 포트(9092)는 **host 에 바인딩하지 않는다** (`ports`
매핑 제거, `expose` 만). 같은 compose 네트워크의 identity-service 는 서비스명(`diary-service:9092`)으로
접근하므로 host 노출이 불필요하다. 내부 디버깅은 `docker compose exec` 또는 임시 ports 매핑으로 수행.

### 2. prod 배포 BLOCK 조건 (전부 충족 전까지 prod 진입 금지)

- [ ] **(B1)** gRPC 포트가 외부망에 노출되지 않음 — k8s NetworkPolicy / 내부 service / 보안 그룹으로
      identity-service ↔ diary-service 만 허용.
- [ ] **(B2)** service-to-service 인증 — gRPC mTLS **또는** authz ServerInterceptor (호출자 신원
      검증: SPIFFE / 내부 service JWT).
- [ ] **(B3)** authz interceptor 도입 시 `include_private=true` 를 **server-side 로 본인 한정 강제** —
      호출자 토큰 subject == author_id 일 때만 전체 카운트 허용. include_private 신뢰 경계를 server 로
      이전 (security-reviewer H1).
- [ ] **(B4)** gRPC server-side rate limit / concurrency 제한 (security-reviewer H2 — 인증 없는 count
      반복 호출 DoS 방어).

### 3. 위협 모델 (현 상태)

- **누설 대상**: 사용자별 비공개 일기 존재/규모 (활동량 PII). 일기 본문은 미노출 (count 만).
- **존재 여부 누설**: 낮음 — 존재하지 않는 authorId 도 count=0 반환, "일기 없는 실유저"와 구분 불가 +
  UUID 추측난.
- **로그**: authorId(UUID) / status / exception class 만 — count / email / token 미노출 (sanitized).

## 근거

- dev 단계에서 mTLS / authz interceptor 도입은 과도 — Slice 3-b 의 핵심(diaryCount 합성)을 먼저 검증하고
  보안 강화는 prod 진입 전 별도 PR 로 분리.
- host 포트 제거는 즉시 적용 가능한 1차 방어 (외부 노출 차단) — 비용 0.
- include_private server-side 강제는 authz interceptor 선행 필요 (호출자 신원 검증 인프라) — prod 조건.

## 결과

- dev compose 는 host 노출 없이 컨테이너간 통신만 — C1 1차 해소.
- prod 진입 PR 은 본 문서 § 2 의 B1~B4 체크리스트를 충족해야 머지 — 미충족 시 prod 배포 BLOCK.
- include_private 신뢰 경계 이전(B3)은 gRPC authz 인프라 도입 시 함께.
