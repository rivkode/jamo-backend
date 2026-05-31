package app.backend.jamo.identity.infrastructure.grpc.client;

import app.backend.jamo.contracts.proto.diary.DiaryQueryServiceGrpc;
import app.backend.jamo.contracts.proto.diary.GetDiaryCountRequest;
import app.backend.jamo.contracts.proto.diary.GetDiaryCountResponse;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DiaryCountGrpcClient 어댑터 단위 테스트 — InProcess gRPC transport (server stub mock impl).
 *
 * <p>Resilience4j {@code @CircuitBreaker}/{@code @Retry} 는 Spring AOP proxy 에서만 동작 — 본 단위 테스트는
 * 어댑터의 raw 응답 매핑 (OK→count / FAILED→null / gRPC error→throw) 을 검증. fallback (예외→null) 은
 * Resilience4j 표준 동작 (통합 범위).
 */
class DiaryCountGrpcClientTest {

    private Server server;
    private ManagedChannel channel;

    @AfterEach
    void tearDown() throws InterruptedException {
        if (channel != null) channel.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
        if (server != null) server.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
    }

    /** 주어진 server-side 동작으로 InProcess server 를 띄우고 client 어댑터를 생성한다. */
    private DiaryCountGrpcClient clientWith(
            BiConsumer<GetDiaryCountRequest, StreamObserver<GetDiaryCountResponse>> behavior) throws IOException {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor()
            .addService(new DiaryQueryServiceGrpc.DiaryQueryServiceImplBase() {
                @Override
                public void getDiaryCount(GetDiaryCountRequest request,
                                          StreamObserver<GetDiaryCountResponse> responseObserver) {
                    behavior.accept(request, responseObserver);
                }
            })
            .build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        return new DiaryCountGrpcClient(DiaryQueryServiceGrpc.newBlockingStub(channel));
    }

    @Test
    void returns_count_when_status_OK() throws IOException {
        DiaryCountGrpcClient client = clientWith((req, obs) -> {
            obs.onNext(GetDiaryCountResponse.newBuilder()
                .setStatus("OK").setCount(11L).setRequestId(req.getRequestId()).build());
            obs.onCompleted();
        });

        Long count = client.getCount(UUID.randomUUID(), true);

        assertThat(count).isEqualTo(11L);
    }

    @Test
    void sends_include_private_flag_to_server() throws IOException {
        boolean[] received = {false};
        DiaryCountGrpcClient client = clientWith((req, obs) -> {
            received[0] = req.getIncludePrivate();
            obs.onNext(GetDiaryCountResponse.newBuilder().setStatus("OK").setCount(0).build());
            obs.onCompleted();
        });

        client.getCount(UUID.randomUUID(), true);

        assertThat(received[0]).isTrue();
    }

    @Test
    void returns_null_when_status_FAILED() throws IOException {
        DiaryCountGrpcClient client = clientWith((req, obs) -> {
            obs.onNext(GetDiaryCountResponse.newBuilder().setStatus("FAILED").setCount(0).build());
            obs.onCompleted();
        });

        // status=FAILED → null (gRPC error 아니므로 retry 안 함)
        assertThat(client.getCount(UUID.randomUUID(), false)).isNull();
    }

    @Test
    void returns_null_when_status_unknown() throws IOException {
        DiaryCountGrpcClient client = clientWith((req, obs) -> {
            obs.onNext(GetDiaryCountResponse.newBuilder().setStatus("WEIRD").setCount(99).build());
            obs.onCompleted();
        });

        assertThat(client.getCount(UUID.randomUUID(), true)).isNull();
    }

    @Test
    void rethrows_StatusRuntimeException_on_grpc_error() throws IOException {
        // gRPC 시스템 오류 → raw throw (Resilience4j Retry 트리거 → 최종 fallback=null, AOP 범위).
        DiaryCountGrpcClient client = clientWith((req, obs) ->
            obs.onError(Status.UNAVAILABLE.withDescription("down").asRuntimeException()));

        assertThatThrownBy(() -> client.getCount(UUID.randomUUID(), true))
            .isInstanceOf(StatusRuntimeException.class);
    }
}
