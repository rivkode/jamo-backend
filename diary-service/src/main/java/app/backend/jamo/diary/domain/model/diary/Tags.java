package app.backend.jamo.diary.domain.model.diary;

import app.backend.jamo.diary.domain.exception.InvalidTagException;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 태그 컬렉션 VO.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §3 (max 10) / §6 (free-form).
 *
 * <p><b>invariant</b>:
 * <ul>
 *   <li>최대 10개</li>
 *   <li>중복 금지 — 동일 {@link Tag#value()} 가 두 번 등장 시 invariant 위반 (사용자 입력 정리는 클라이언트 책임)</li>
 *   <li>각 element 는 {@link Tag} 자체 invariant 통과 (1..30 code points)</li>
 *   <li>빈 리스트 허용 — 태그 없이 작성 가능</li>
 * </ul>
 *
 * <p>응답 직렬화 시 {@link #values()} 로 raw String 리스트 추출.
 */
public record Tags(List<Tag> values) {

    public static final int MAX_SIZE = 10;

    public Tags {
        Objects.requireNonNull(values, "values");
        if (values.size() > MAX_SIZE) {
            throw new InvalidTagException(
                "tags size out of range: max " + MAX_SIZE + ", got " + values.size()
            );
        }
        Set<String> seen = new HashSet<>();
        for (Tag tag : values) {
            Objects.requireNonNull(tag, "tag element");
            if (!seen.add(tag.value())) {
                throw new InvalidTagException("duplicate tag: " + tag.value());
            }
        }
        values = List.copyOf(values);
    }

    public static Tags empty() {
        return new Tags(List.of());
    }

    public static Tags ofStrings(List<String> raw) {
        Objects.requireNonNull(raw, "raw");
        List<Tag> tags = new java.util.ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            String s = raw.get(i);
            if (s == null) {
                throw new InvalidTagException("tag at index " + i + " is null");
            }
            tags.add(new Tag(s));
        }
        return new Tags(tags);
    }

    public List<String> asStrings() {
        return values.stream().map(Tag::value).toList();
    }

    public int size() {
        return values.size();
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }
}
