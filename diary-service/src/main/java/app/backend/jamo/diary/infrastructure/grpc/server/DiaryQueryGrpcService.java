package app.backend.jamo.diary.infrastructure.grpc.server;

import app.backend.jamo.contracts.proto.diary.DiaryQueryServiceGrpc;
import app.backend.jamo.contracts.proto.diary.GetDiaryCountRequest;
import app.backend.jamo.contracts.proto.diary.GetDiaryCountResponse;
import app.backend.jamo.diary.application.dto.diary.GetDiaryCountQuery;
import app.backend.jamo.diary.application.service.diary.GetDiaryCountService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.UUID;

/**
 * {@code DiaryQueryService} gRPC server — diary-service 가 처음 노출하는 gRPC server (Slice 3-b).
 *
 * <p>박제: contracts/diary.proto + PRD 0526_flutter.md §1.5 / §1.6. 호출자: identity-service 의
 * 프로필 조회 (diaryCount 합성).
 *
 * <p><b>응답 정책</b> (identity.proto UserSummaryService 의 body status 패턴 정합):
 * <ul>
 *   <li>정상 → status="OK" + count.</li>
 *   <li>author_id 가 UUID 가 아니면 → gRPC {@code INVALID_ARGUMENT} (호출 측 프로그래밍 오류).</li>
 *   <li>DB 등 내부 오류 → status="FAILED" + count=0 (gRPC 200). 호출 측 (identity) 이 fallback
 *       (diaryCount=null) 결정. 단순 read 라 gRPC error 보다 body status 로 UX 단순화.</li>
 * </ul>
 *
 * <p>Application Service ({@link GetDiaryCountService}) 경유 — Repository 직접 호출 금지 (module-boundary).
 * request_id 는 비어 있으면 server 가 생성하여 echo (분산 trace).
 */
@GrpcService
@RequiredArgsConstructor
@Slf4j
public class DiaryQueryGrpcService extends DiaryQueryServiceGrpc.DiaryQueryServiceImplBase {

    private final GetDiaryCountService getDiaryCountService;

    @Override
    public void getDiaryCount(GetDiaryCountRequest request,
                              StreamObserver<GetDiaryCountResponse> responseObserver) {
        String requestId = request.getRequestId().isBlank()
            ? UUID.randomUUID().toString()
            : request.getRequestId();

        UUID authorId;
        try {
            authorId = UUID.fromString(request.getAuthorId());
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription("author_id must be a valid UUID")
                .asRuntimeException());
            return;
        }

        try {
            long count = getDiaryCountService.count(
                new GetDiaryCountQuery(authorId, request.getIncludePrivate()));
            responseObserver.onNext(GetDiaryCountResponse.newBuilder()
                .setStatus("OK")
                .setCount(count)
                .setRequestId(requestId)
                .build());
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            // 내부 오류 — body status="FAILED" 로 응답 (gRPC error 아님). 호출 측 fallback 결정.
            log.warn("GetDiaryCount failed: authorId={} includePrivate={} requestId={}",
                authorId, request.getIncludePrivate(), requestId, e);
            responseObserver.onNext(GetDiaryCountResponse.newBuilder()
                .setStatus("FAILED")
                .setCount(0)
                .setRequestId(requestId)
                .build());
            responseObserver.onCompleted();
        }
    }
}
