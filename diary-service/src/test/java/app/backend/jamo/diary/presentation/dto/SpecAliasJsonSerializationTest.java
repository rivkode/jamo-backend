package app.backend.jamo.diary.presentation.dto;

import app.backend.jamo.diary.application.dto.diary.DiaryView;
import app.backend.jamo.diary.application.dto.diary.ToggleDiaryLikeView;
import app.backend.jamo.diary.domain.model.diary.Visibility;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PRD 0526_flutter.md §2 정합 alias 직렬화 단위 테스트 (Slice 2).
 *
 * <p>{@link DiaryResponse} / {@link ToggleDiaryLikeResponse} 가 기존 필드 + alias 필드를 동시에 노출하는지 검증.
 */
class SpecAliasJsonSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final UUID DIARY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID AUTHOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-05-27T10:00:00Z");

    private static DiaryView sampleView(Visibility visibility, boolean likedByMe) {
        return new DiaryView(
            DIARY_ID, AUTHOR_ID, "Minji", List.of("hello", "line2", "line3"), List.of(), List.of(),
            visibility, 3, 1, likedByMe, NOW
        );
    }

    @Test
    void DiaryResponse_exposes_isPublic_alias_when_PUBLIC() throws Exception {
        DiaryResponse res = DiaryResponse.from(sampleView(Visibility.PUBLIC, false));

        JsonNode node = mapper.readTree(mapper.writeValueAsString(res));

        assertThat(node.get("visibility").asText()).isEqualTo("PUBLIC");
        assertThat(node.get("isPublic").asBoolean()).isTrue(); // PRD §2 alias
    }

    @Test
    void DiaryResponse_isPublic_alias_is_false_when_PRIVATE() throws Exception {
        DiaryResponse res = DiaryResponse.from(sampleView(Visibility.PRIVATE, false));

        JsonNode node = mapper.readTree(mapper.writeValueAsString(res));

        assertThat(node.get("visibility").asText()).isEqualTo("PRIVATE");
        assertThat(node.get("isPublic").asBoolean()).isFalse();
    }

    @Test
    void DiaryResponse_exposes_userLiked_alias_synonymous_with_likedByMe() throws Exception {
        DiaryResponse res = DiaryResponse.from(sampleView(Visibility.PUBLIC, true));

        JsonNode node = mapper.readTree(mapper.writeValueAsString(res));

        assertThat(node.get("likedByMe").asBoolean()).isTrue();
        assertThat(node.get("userLiked").asBoolean()).isTrue(); // PRD §2 alias
    }

    @Test
    void DiaryResponse_exposes_author_object_alias() throws Exception {
        DiaryResponse res = DiaryResponse.from(sampleView(Visibility.PUBLIC, false));

        JsonNode node = mapper.readTree(mapper.writeValueAsString(res));

        // 기존 평탄 필드도 그대로 노출
        assertThat(node.get("authorId").asText()).isEqualTo(AUTHOR_ID.toString());
        assertThat(node.get("authorDisplayName").asText()).isEqualTo("Minji");
        // PRD §2 alias: {userId, username, avatarUrl}
        JsonNode author = node.get("author");
        assertThat(author).isNotNull();
        assertThat(author.get("userId").asText()).isEqualTo(AUTHOR_ID.toString());
        assertThat(author.get("username").asText()).isEqualTo("Minji");
        assertThat(author.has("avatarUrl")).isTrue();
        assertThat(author.get("avatarUrl").isNull()).isTrue();
        // TODO: avatar 도메인 도입 시 DiaryView 에 avatarUrl 추가 + 본 단정 갱신 (test-reviewer L2).
    }

    @Test
    void ToggleDiaryLikeResponse_exposes_userLiked_alias() throws Exception {
        ToggleDiaryLikeResponse res = ToggleDiaryLikeResponse.from(
            new ToggleDiaryLikeView(DIARY_ID, true, 7));

        JsonNode node = mapper.readTree(mapper.writeValueAsString(res));

        assertThat(node.get("liked").asBoolean()).isTrue();
        assertThat(node.get("userLiked").asBoolean()).isTrue(); // PRD §2.7 alias
        assertThat(node.get("likeCount").asInt()).isEqualTo(7);
        assertThat(node.get("diaryId").asText()).isEqualTo(DIARY_ID.toString());
    }
}
