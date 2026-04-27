package app.backend.jamo.identity.domain.model.user;

import java.util.Objects;

/**
 * {@link DisplayName#truncated(String)} 의 변환 결과.
 * wasTruncated 는 도메인 불변식이 아니라 변환 메타이므로 별도 record 로 분리.
 * SPA 가 사용자에게 "수정하시겠어요?" 안내를 띄우도록 클라이언트로 노출.
 */
public record DisplayNameTruncation(DisplayName displayName, boolean wasTruncated) {

    public DisplayNameTruncation {
        Objects.requireNonNull(displayName, "displayName");
    }
}
