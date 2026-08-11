package com.ktb.chatapp.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FileUrl 단위 테스트")
class FileUrlTest {

    @Test
    @DisplayName("S3 모드에서는 key 앞에 CloudFront 도메인을 붙인다")
    void of_prependsCloudFrontDomainForS3() {
        FileUrl fileUrl = new FileUrl("s3", "https://d16225pinz5a60.cloudfront.net/");

        assertThat(fileUrl.of("profiles/avatar.png"))
                .isEqualTo("https://d16225pinz5a60.cloudfront.net/profiles/avatar.png");
    }

    @Test
    @DisplayName("로컬 저장 모드의 프로필 key는 공개 프로필 파일 API를 사용한다")
    void of_returnsProfileApiPathForLocalStorage() {
        FileUrl fileUrl = new FileUrl("local", "https://d16225pinz5a60.cloudfront.net");

        assertThat(fileUrl.of("profiles/avatar.png")).isEqualTo("/api/files/profiles/avatar.png");
    }

    @Test
    @DisplayName("로컬 저장 모드의 채팅 key는 실제 미리보기 API를 사용한다")
    void of_returnsViewApiPathForLocalChatFile() {
        FileUrl fileUrl = new FileUrl("local", "");

        assertThat(fileUrl.of("chat/attachment.png"))
                .isEqualTo("/api/files/view/attachment.png");
    }

    @Test
    @DisplayName("S3 모드라도 CDN 도메인이 없으면 백엔드 프록시를 사용한다")
    void of_returnsViewApiPathWhenCdnIsNotConfigured() {
        FileUrl fileUrl = new FileUrl("s3", "");

        assertThat(fileUrl.of("chat/attachment.png"))
                .isEqualTo("/api/files/view/attachment.png");
    }

    @Test
    @DisplayName("이미 완성된 URL은 변경하지 않는다")
    void of_passesThroughAbsoluteUrl() {
        FileUrl fileUrl = new FileUrl("s3", "https://d16225pinz5a60.cloudfront.net");

        assertThat(fileUrl.of("https://legacy.example.test/avatar.png"))
                .isEqualTo("https://legacy.example.test/avatar.png");
    }

    @Test
    @DisplayName("of()는 값이 없으면 그대로 통과시킨다")
    void of_passesThroughEmptyValues() {
        FileUrl fileUrl = new FileUrl("s3", "https://d16225pinz5a60.cloudfront.net");

        assertThat(fileUrl.of(null)).isNull();
        assertThat(fileUrl.of("")).isEmpty();
    }
}
