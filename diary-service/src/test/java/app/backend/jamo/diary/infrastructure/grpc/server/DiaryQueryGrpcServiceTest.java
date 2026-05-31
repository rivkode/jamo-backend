package app.backend.jamo.diary.infrastructure.grpc.server;

import app.backend.jamo.contracts.proto.diary.GetDiaryCountRequest;
import app.backend.jamo.contracts.proto.diary.GetDiaryCountResponse;
import app.backend.jamo.diary.application.dto.diary.GetDiaryCountQuery;
import app.backend.jamo.diary.application.service.diary.GetDiaryCountService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiaryQueryGrpcServiceTest {

    private GetDiaryCountService getDiaryCountService;
    private DiaryQueryGrpcService grpcService;

    @BeforeEach
    void setUp() {
        getDiaryCountService = mock(GetDiaryCountService.class);
        grpcService = new DiaryQueryGrpcService(getDiaryCountService);
    }

    @SuppressWarnings("unchecked")
    private StreamObserver<GetDiaryCountResponse> mockObserver() {
        return mock(StreamObserver.class);
    }

    @Test
    void getDiaryCount_returns_OK_with_count_and_echoes_request_id() {
        UUID author = UUID.randomUUID();
        when(getDiaryCountService.count(any())).thenReturn(9L);
        StreamObserver<GetDiaryCountResponse> observer = mockObserver();

        grpcService.getDiaryCount(GetDiaryCountRequest.newBuilder()
            .setAuthorId(author.toString())
            .setIncludePrivate(true)
            .setRequestId("req-1")
            .build(), observer);

        ArgumentCaptor<GetDiaryCountResponse> captor = ArgumentCaptor.forClass(GetDiaryCountResponse.class);
        verify(observer).onNext(captor.capture());
        verify(observer).onCompleted();
        GetDiaryCountResponse response = captor.getValue();
        assertThat(response.getStatus()).isEqualTo("OK");
        assertThat(response.getCount()).isEqualTo(9L);
        assertThat(response.getRequestId()).isEqualTo("req-1");

        // includePrivate 플래그가 Query 로 정확히 전달되는지
        ArgumentCaptor<GetDiaryCountQuery> queryCaptor = ArgumentCaptor.forClass(GetDiaryCountQuery.class);
        verify(getDiaryCountService).count(queryCaptor.capture());
        assertThat(queryCaptor.getValue().authorId()).isEqualTo(author);
        assertThat(queryCaptor.getValue().includePrivate()).isTrue();
    }

    @Test
    void getDiaryCount_generates_request_id_when_blank() {
        UUID author = UUID.randomUUID();
        when(getDiaryCountService.count(any())).thenReturn(0L);
        StreamObserver<GetDiaryCountResponse> observer = mockObserver();

        grpcService.getDiaryCount(GetDiaryCountRequest.newBuilder()
            .setAuthorId(author.toString())
            .setIncludePrivate(false)
            .build(), observer);

        ArgumentCaptor<GetDiaryCountResponse> captor = ArgumentCaptor.forClass(GetDiaryCountResponse.class);
        verify(observer).onNext(captor.capture());
        // 빈 request_id → server 생성 UUID echo
        assertThat(captor.getValue().getRequestId()).isNotBlank();
        UUID.fromString(captor.getValue().getRequestId()); // valid UUID
    }

    @Test
    void getDiaryCount_returns_INVALID_ARGUMENT_when_author_id_not_uuid() {
        StreamObserver<GetDiaryCountResponse> observer = mockObserver();

        grpcService.getDiaryCount(GetDiaryCountRequest.newBuilder()
            .setAuthorId("not-a-uuid")
            .build(), observer);

        ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(observer).onError(errorCaptor.capture());
        verify(observer, never()).onNext(any());
        StatusRuntimeException ex = (StatusRuntimeException) errorCaptor.getValue();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        // service 미호출 (programming error short-circuit)
        verify(getDiaryCountService, never()).count(any());
    }

    @Test
    void getDiaryCount_returns_FAILED_status_when_service_throws() {
        UUID author = UUID.randomUUID();
        when(getDiaryCountService.count(any())).thenThrow(new RuntimeException("db down"));
        StreamObserver<GetDiaryCountResponse> observer = mockObserver();

        grpcService.getDiaryCount(GetDiaryCountRequest.newBuilder()
            .setAuthorId(author.toString())
            .setRequestId("req-2")
            .build(), observer);

        ArgumentCaptor<GetDiaryCountResponse> captor = ArgumentCaptor.forClass(GetDiaryCountResponse.class);
        verify(observer).onNext(captor.capture());
        verify(observer).onCompleted();
        // 내부 오류 → gRPC error 아닌 body status="FAILED" (호출 측 fallback 결정)
        assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getCount()).isZero();
        assertThat(captor.getValue().getRequestId()).isEqualTo("req-2");
        verify(observer, never()).onError(any());
    }
}
