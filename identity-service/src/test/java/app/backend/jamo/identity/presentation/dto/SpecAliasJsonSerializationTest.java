package app.backend.jamo.identity.presentation.dto;

import app.backend.jamo.identity.application.dto.MyProfileResult;
import app.backend.jamo.identity.application.dto.PublicProfileResult;
import app.backend.jamo.identity.application.dto.RegisterUserResult;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.domain.model.profile.AvatarUrl;
import app.backend.jamo.identity.domain.model.profile.Bio;
import app.backend.jamo.identity.domain.model.profile.Locale;
import app.backend.jamo.identity.domain.model.user.DisplayName;
import app.backend.jamo.identity.domain.model.user.Email;
import app.backend.jamo.identity.domain.model.user.UserId;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PRD 0526_flutter.md §0 / §1 정합 alias 직렬화 단위 테스트 (Slice 2).
 *
 * <p>각 DTO 가 (a) 기존 필드 + (b) PRD alias 필드를 동시에 노출하는지 검증 — 양방향 호환.
 * Spring 의 글로벌 ObjectMapper 설정과 무관하게 record + {@code @JsonProperty} 어노테이션만으로 동작해야
 * 하므로 plain ObjectMapper 로 검증.
 */
class SpecAliasJsonSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final UUID USER_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-05-27T10:00:00Z");

    @Test
    void RegisterUserResponse_exposes_username_alias() throws Exception {
        RegisterUserResponse res = RegisterUserResponse.from(new RegisterUserResult(
            USER_UUID, "u@jamoai.app", "Minji", NOW));

        JsonNode node = mapper.readTree(mapper.writeValueAsString(res));

        assertThat(node.get("displayName").asText()).isEqualTo("Minji");
        assertThat(node.get("username").asText()).isEqualTo("Minji"); // PRD §1.2 alias
        assertThat(node.get("userId").asText()).isEqualTo(USER_UUID.toString());
        assertThat(node.get("email").asText()).isEqualTo("u@jamoai.app");
    }

    @Test
    void MyProfileResponse_exposes_username_and_provider_singular_alias() throws Exception {
        MyProfileResponse res = MyProfileResponse.from(new MyProfileResult(
            new UserId(USER_UUID),
            new Email("u@jamoai.app"),
            new DisplayName("Minji"),
            List.of(OAuthProvider.GOOGLE, OAuthProvider.KAKAO),
            NOW,
            new Bio("hello"),
            new AvatarUrl("https://e.io/a.png"),
            new Locale("ko"),
            7L));

        JsonNode node = mapper.readTree(mapper.writeValueAsString(res));

        assertThat(node.get("displayName").asText()).isEqualTo("Minji");
        assertThat(node.get("username").asText()).isEqualTo("Minji"); // PRD §1.5 alias
        assertThat(node.get("providers")).hasSize(2);
        assertThat(node.get("provider").asText()).isEqualTo("GOOGLE"); // PRD §1.5 단수 — providers[0]
        assertThat(node.get("diaryCount").asLong()).isEqualTo(7L); // Slice 3-b
    }

    @Test
    void MyProfileResponse_provider_is_null_when_providers_empty() throws Exception {
        MyProfileResponse res = MyProfileResponse.from(new MyProfileResult(
            new UserId(USER_UUID),
            new Email("u@jamoai.app"),
            new DisplayName("Minji"),
            List.of(),
            NOW,
            null,
            null,
            new Locale("ko"),
            null));

        JsonNode node = mapper.readTree(mapper.writeValueAsString(res));

        // PRD: provider 가 null 일 수 있다 — 키 명시 노출 (Include.ALWAYS).
        assertThat(node.has("provider")).isTrue();
        assertThat(node.get("provider").isNull()).isTrue();
        // Slice 3-b: diaryCount 도 null 일 수 있다 (gRPC 실패) — 키 명시 노출.
        assertThat(node.has("diaryCount")).isTrue();
        assertThat(node.get("diaryCount").isNull()).isTrue();
    }

    @Test
    void PublicProfileResponse_exposes_username_alias() throws Exception {
        PublicProfileResponse res = PublicProfileResponse.from(new PublicProfileResult(
            new UserId(USER_UUID),
            new DisplayName("Minji"),
            new Bio("hi"),
            new AvatarUrl("https://e.io/a.png"),
            3L));

        JsonNode node = mapper.readTree(mapper.writeValueAsString(res));

        assertThat(node.get("displayName").asText()).isEqualTo("Minji");
        assertThat(node.get("username").asText()).isEqualTo("Minji"); // PRD §1.6 alias
        assertThat(node.get("diaryCount").asLong()).isEqualTo(3L); // Slice 3-b — 공개 일기만
    }

    @Test
    void AuthExchangeResponse_exposes_expiresIn_and_tokenType_aliases() throws Exception {
        AuthExchangeResponse res = new AuthExchangeResponse(
            USER_UUID.toString(), "access-jwt", "refresh-jwt", 432000);

        JsonNode node = mapper.readTree(mapper.writeValueAsString(res));

        assertThat(node.get("expiresInSeconds").asLong()).isEqualTo(432000);
        assertThat(node.get("expiresIn").asLong()).isEqualTo(432000); // PRD §0.2 alias
        assertThat(node.get("tokenType").asText()).isEqualTo("Bearer"); // PRD §0.2 고정
        assertThat(node.get("accessToken").asText()).isEqualTo("access-jwt");
        assertThat(node.get("refreshToken").asText()).isEqualTo("refresh-jwt");
    }

    @Test
    void RegisterUserRequest_accepts_username_field_as_displayName_alias() throws Exception {
        // PRD §1.2 — frontend 가 username 키로 보내도 displayName 으로 매핑 (@JsonAlias).
        String json = """
            {"email":"u@jamoai.app","password":"Passw0rd!","username":"Minji"}
            """;

        RegisterUserRequest req = mapper.readValue(json, RegisterUserRequest.class);

        assertThat(req.email()).isEqualTo("u@jamoai.app");
        assertThat(req.password()).isEqualTo("Passw0rd!");
        assertThat(req.displayName()).isEqualTo("Minji");
    }

    @Test
    void RegisterUserRequest_still_accepts_displayName_field_directly() throws Exception {
        String json = """
            {"email":"u@jamoai.app","password":"Passw0rd!","displayName":"Minji"}
            """;

        RegisterUserRequest req = mapper.readValue(json, RegisterUserRequest.class);

        assertThat(req.displayName()).isEqualTo("Minji");
    }

    @Test
    void RegisterUserRequest_when_both_username_and_displayName_provided_fails_fast() throws Exception {
        // Jackson record + @JsonAlias 동작 박제 — alias 와 정식 키 동시 제공 시 InvalidDefinitionException
        // 으로 fail-fast (last-wins 아님). frontend 가 실수로 둘 다 보내면 400 응답 (Spring MVC 의
        // HttpMessageNotReadableException 매핑). 의도된 안전한 reject — 양쪽 값 충돌 모호성 회피.
        String both = """
            {"email":"u@jamoai.app","password":"Passw0rd!","username":"A","displayName":"B"}
            """;

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                mapper.readValue(both, RegisterUserRequest.class))
            .isInstanceOf(com.fasterxml.jackson.databind.exc.InvalidDefinitionException.class);
    }

    @Test
    void AuthExchangeResponse_invariant_expiresInSeconds_must_be_positive() {
        // Slice 2 alias 도입 후에도 record compact constructor 의 invariant 가 유지되는지 회귀 보호.
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new AuthExchangeResponse(USER_UUID.toString(), "a", "r", 0))
            .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new AuthExchangeResponse(USER_UUID.toString(), "a", "r", -1))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
