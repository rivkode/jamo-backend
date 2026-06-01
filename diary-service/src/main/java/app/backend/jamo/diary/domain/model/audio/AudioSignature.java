package app.backend.jamo.diary.domain.model.audio;

import app.backend.jamo.diary.domain.exception.InvalidAudioException;

/**
 * 업로드 본문의 magic byte 검사 — 선언된 content-type 헤더는 신뢰할 수 없으므로(클라이언트 위조 가능),
 * 실제 바이트가 오디오로 보이는지 확인해 HTML/스크립트 위장 업로드(저장형 XSS 벡터)를 차단한다.
 *
 * <p>정책: 알려진 오디오 시그니처면 통과, 텍스트/마크업({@code <} 으로 시작 — HTML/SVG/XML)이면 거부,
 * 그 외 미상 바이너리는 관대 통과(서빙 측 {@code nosniff}+CSP sandbox+Content-Disposition 가 2차 방어).
 * 즉 "확실한 비오디오(마크업)"만 막아 정상 오디오 오탐을 0 으로 둔다.
 */
public final class AudioSignature {

    private AudioSignature() {
    }

    /** content 가 명백히 오디오가 아닌 마크업/텍스트면 {@link InvalidAudioException}. */
    public static void rejectIfNotAudio(byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidAudioException("audio content is empty");
        }
        if (isKnownAudio(content)) {
            return;
        }
        if (looksLikeMarkup(content)) {
            throw new InvalidAudioException("uploaded content is not audio (markup/text detected)");
        }
        // 미상 바이너리 — 서빙 측 nosniff/CSP 가 방어. 정상 오디오 오탐 회피 위해 통과.
    }

    private static boolean isKnownAudio(byte[] b) {
        return matchesAscii(b, 0, "RIFF") && matchesAscii(b, 8, "WAVE")   // wav
            || matchesAscii(b, 0, "ID3")                                   // mp3 (ID3v2)
            || (u(b, 0) == 0xFF && (u(b, 1) & 0xE0) == 0xE0)              // mp3 frame sync / aac ADTS
            || matchesAscii(b, 0, "OggS")                                  // ogg
            || (u(b, 0) == 0x1A && u(b, 1) == 0x45 && u(b, 2) == 0xDF && u(b, 3) == 0xA3) // webm (EBML)
            || matchesAscii(b, 4, "ftyp");                                 // mp4 / m4a
    }

    private static boolean looksLikeMarkup(byte[] b) {
        int i = 0;
        // 선행 공백 / UTF-8 BOM 건너뜀
        if (b.length >= 3 && u(b, 0) == 0xEF && u(b, 1) == 0xBB && u(b, 2) == 0xBF) {
            i = 3;
        }
        while (i < b.length && Character.isWhitespace(b[i])) {
            i++;
        }
        return i < b.length && b[i] == '<';
    }

    private static boolean matchesAscii(byte[] b, int offset, String ascii) {
        if (b.length < offset + ascii.length()) {
            return false;
        }
        for (int i = 0; i < ascii.length(); i++) {
            if (b[offset + i] != (byte) ascii.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static int u(byte[] b, int i) {
        return i < b.length ? (b[i] & 0xFF) : -1;
    }
}
