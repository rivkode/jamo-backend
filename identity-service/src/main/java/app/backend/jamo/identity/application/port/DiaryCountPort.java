package app.backend.jamo.identity.application.port;

import java.util.UUID;

/**
 * 다른 BC (diary-service) 의 사용자별 일기 수 조회 port (Slice 3-b / PRD 0526_flutter.md §1.5·§1.6).
 *
 * <p>profile 응답의 {@code diaryCount} 합성 전용 — 도메인 불변식과 무관한 read-only 외부 집계 조회이므로
 * domain 이 아닌 application 계층 port (diary-service 의 {@code UserSummaryPort} 정합). 구현체는
 * Infrastructure 의 gRPC 어댑터 ({@code DiaryCountGrpcClient}).
 *
 * <p><b>실패 시 null</b>: gRPC 장애 / Circuit OPEN / 응답 status="FAILED" 모두 {@code null} 반환 — 호출
 * 측(profile service)이 응답에 그대로 노출 (프론트가 "집계 불가" → "-" 표시). 0 (일기 없음) 과 구분.
 */
public interface DiaryCountPort {

    /**
     * 사용자별 일기 수 조회.
     *
     * @param authorId       대상 사용자
     * @param includePrivate true = 전체 (PUBLIC + PRIVATE) 본인 프로필 / false = PUBLIC 만 타인 프로필 (IDOR 차단)
     * @return 일기 수, 조회 실패 시 {@code null}
     */
    Long getCount(UUID authorId, boolean includePrivate);
}
