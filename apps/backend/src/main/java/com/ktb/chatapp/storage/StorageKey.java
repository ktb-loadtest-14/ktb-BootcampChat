package com.ktb.chatapp.storage;

/**
 * 스토리지 key 규약: {@code profiles/<name>}, {@code chat/<name>}. 공개 CDN 사용 시 두 key 모두
 * CloudFront 읽기 URL로 노출될 수 있다.
 */
public final class StorageKey {

    private static final String PROFILE_PREFIX = "profiles/";
    private static final String CHAT_PREFIX = "chat/";

    private StorageKey() {
    }

    public static String profile(String fileName) {
        return PROFILE_PREFIX + fileName;
    }

    public static String chat(String fileName) {
        return CHAT_PREFIX + fileName;
    }

    public static boolean isProfile(String key) {
        return key != null && key.startsWith(PROFILE_PREFIX);
    }

    public static boolean isChat(String key) {
        return key != null && key.startsWith(CHAT_PREFIX);
    }

    public static String nameOf(String key) {
        if (isProfile(key)) {
            return key.substring(PROFILE_PREFIX.length());
        }
        if (isChat(key)) {
            return key.substring(CHAT_PREFIX.length());
        }
        return key;
    }
}
