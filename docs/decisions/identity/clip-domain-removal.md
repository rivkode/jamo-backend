# Decision: clip 도메인 전면 폐기 — `listSavedClips` 및 관련 테이블 미생성

- **상태**: Accepted
- **결정일**: 2026-04-28
- **결정자**: jonghun
- **PR**: `feature/identity-clip-domain-removal` (Track A — profile 평가 선행 PR)
- **관련 ADR**: [ADR-0002 서비스 분할](../../adr/0002-service-decomposition.md), [ADR-0007 contracts-first 병렬 개발](../../adr/0007-contracts-first-parallel-development.md)
- **관련 결정**: [identity/user-profile-domain-boundary.md](user-profile-domain-boundary.md) — 본 결정 시점 이전에 머지된 결정 문서로 `savedClips` 가 언급되나 *그 시점의 컨텍스트* 보존을 위해 retroactive 수정 안 함
- **관련 PRD**: ~~[`prd/profile/listSavedClips.md`](../../prd/profile/listSavedClips.md)~~ (본 결정과 함께 삭제)

## 컨텍스트

profile 도메인 PRD 일괄 평가 (Phase 6-a) 진입 직전, 4번째 endpoint 인 `listSavedClips` (`GET /api/v1/profiles/{userId}/saved-clips`) 의 도메인 경계를 검토했다. 다음 사실들이 동시에 관찰됐다.

1. **clip / saved_clip 은 본 프로젝트의 어떤 PRD 에서도 정의/사용되지 않음** — `_index.md` 의 13개 도메인 (auth / chat / comment / diary / diarychat / event / feedback / profile / sentence / shorts / user / validation / word) 중 어디에도 `clip` 도메인이 없다. `shorts` 가 가장 인접하지만 PRD `shorts/listFeed.md` 는 *URL 큐레이션* 만 다루며 clip 개념 미사용.
2. **legacy 코드에서만 흔적이 잔존** — `listSavedClips` PRD 는 `status: mined` 로 추출됐으나 `saved_clips × clips (조인)` 만 명시됐을 뿐 본 그린필드 백엔드에서 clip 의 정의 / 라이프사이클 / 생성 경로 PRD 가 없다.
3. **신규 백엔드 서비스 구성에 clip 미포함** — [`service-domain-mapping.md`](../../architecture/service-domain-mapping.md) 의 5개 Java 서비스 (identity / diary / chat / learning / platform) + 1개 Python (ai-service) 어디에도 clip 도메인 책임이 할당되지 않음. 가장 가까운 platform-service 의 도메인은 `shorts + event + feedback` 으로 한정.
4. **사용자 결정**: clip 관련 도메인은 **사용하지 않음**. 관련 기능·내용을 모두 제거.

## 검토한 옵션

| 옵션 | 의미 | 단점 |
|---|---|---|
| **A. profile 도메인 유지 (identity-service)** | identity-service 안에 `saved_clips` 테이블 추가 | profile 가 외부 개념(clip) 을 알게 됨. 도메인 경계 모호. clip 도메인 자체가 본 프로젝트에 없으므로 임포트 불가 |
| **B. platform-service 의 shorts 와 응집 → 이전** | profile §9 는 DROP, platform 측 신규 PRD 작성 권고 | clip 도메인 자체가 미정의. 이전 대상 도메인이 없음 |
| **C. clip 도메인 전면 폐기** | `listSavedClips.md` 삭제, 관련 흔적 제거. saved_clips / clips 테이블 미생성 | (없음 — 본 프로젝트는 사용자 결정으로 clip 미사용) |

### 결정 — 옵션 **C**: clip 도메인 전면 폐기

`listSavedClips` PRD 를 KEEP/FIX/DROP §9 분류 절차에서 *제외* 하지 않고 **PRD 파일 자체를 삭제**한다. profile 도메인은 3 API (`getMyProfile` / `getProfile` / `updateMyProfile`) 로 축소.

## 근거

1. **도메인이 정의되지 않은 endpoint 는 PRD 가 아니라 legacy 잔재** — `listSavedClips` 는 mined 시점의 추출 산물일 뿐 본 그린필드 백엔드의 도메인 모델로 이전된 적이 없다. 분류 (KEEP/FIX/DROP) 는 도메인 모델로 이전될 정당성이 있을 때 하는 것이 맞다.
2. **DROP 만으로는 부족** — DROP 으로 §9 만 채워두면 PRD 파일과 `_index.md` 의 도메인 합계 / endpoint 수 / "Legacy API PRD 인덱스 — 25 컨트롤러 / 59 endpoint" 가 잔존해 문서 정합성을 흐린다. 미사용 도메인은 흔적째 제거하는 것이 향후 reader 의 인지 비용을 낮춘다.
3. **ADR-0007 Track A 정의의 정확성** — Track A 의 범위가 "4 API" 로 박혀 있어 trail 의 정확성을 위해 "3 API" 로 갱신 + cross-reference. 이는 트랙 운영 문서 (살아있는 결정) 이므로 retroactive 수정이 정당.
4. **`user-profile-domain-boundary.md` 는 보존** — 이미 머지된 결정 문서에 `savedClips` 가 Non-Goals / 도메인 책임 예시로 언급되나, 그 시점의 결정 컨텍스트 (=clip 폐기 결정 *이전*) 를 보존하는 것이 결정 history 가치 측면에서 맞다. 본 결정 문서가 cross-reference 로 후속 사실을 명시하므로 future reader 가 두 문서를 함께 읽으면 흐름이 복원된다.

## 결과 및 영향

### 문서 변경 (본 PR)

| 파일 | 변경 |
|---|---|
| `docs/prd/profile/listSavedClips.md` | **삭제** |
| `docs/prd/_index.md` | "59 endpoint" → "58 endpoint", profile 행 "4" → "3", 합계 "59" → "58", profile 섹션 "(4)" → "(3)" + listSavedClips 링크 라인 삭제 |
| `docs/adr/0007-contracts-first-parallel-development.md` | Track A 정의 "4 API (..., listSavedClips)" → "3 API (...)" + 본 결정 cross-reference |
| `docs/decisions/identity/clip-domain-removal.md` | **신규** (본 파일) |

### 코드 변경

- 없음 — `saved_clips` / `clips` 테이블, ProfileSavedClip JpaEntity, `profileFacade.retrieveSavedClips`, `ProfileSavedClipDto` 등 어떤 것도 본 백엔드에 미생성 상태이므로 신규 작성 자체를 *하지 않는다*.

### 후속 영향

- **profile 평가 PR (Phase 6-a, 후속)**: 3 API 만 §9 채움. listSavedClips 는 §9 분류 대상에서 제외 (이미 PRD 부재).
- **profile 코드 슬라이스 (Phase 6-b 이후)**: `Profile` aggregate 에 `savedClips` 컬렉션 / `ProfileSavedClip` Entity / Flyway 의 `saved_clips` 테이블 모두 미정의. 외형 필드 (`displayName / bio / avatarUrl / locale`) 만 모델링.
- **Track 운영 문서**: `_status.md` 의 도메인 요약 표 profile 행 (현재 `총 API 4`) 갱신은 본 PR 에서 수행하지 않음. 병렬 트랙 충돌 회피 규칙 ([feedback memory](#) — 병렬 트랙 운영 시 공유 파일 편집 금지) 으로 aggregator PR 에서 일괄 처리. 본 PR 본문에 위임 명시.

### Non-Goals (본 결정에서 다루지 않음)

- 향후 clip 도메인 부활 가능성 — 본 결정은 *현 시점의 도메인 미사용* 을 박제. 미래에 비즈니스 요구가 생기면 별도 신규 PRD + ADR 로 *그린필드* 도입.
- 다른 legacy 잔재 일괄 청소 — 본 결정은 `clip` 한정. 다른 mined PRD 의 미사용 여부는 각 도메인 평가 시점에 개별 판단.
- `user-profile-domain-boundary.md` 의 retroactive 수정 — 결정 history 보존을 위해 의도적으로 미수행. 후속 reader 는 본 문서 cross-reference 로 흐름 복원.

## 참고

- [ADR-0007](../../adr/0007-contracts-first-parallel-development.md) — Track A 정의 (3 API 로 갱신됨)
- [`docs/prd/_index.md`](../../prd/_index.md) — Legacy PRD 인덱스 (58 endpoint 로 갱신됨)
- [`docs/architecture/service-domain-mapping.md`](../../architecture/service-domain-mapping.md) — 13 도메인 / clip 부재 확인
- [user-profile-domain-boundary.md](user-profile-domain-boundary.md) — 본 결정 *직전* 의 profile 경계 결정 (savedClips 언급 보존)
