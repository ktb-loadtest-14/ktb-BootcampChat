package com.ktb.chatapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.storage.PresignedUpload;
import com.ktb.chatapp.storage.StoragePort;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("프로필 이미지 직접 업로드")
class UserServicePresignedProfileTest {

    private static final String EMAIL = "user@example.com";

    @Mock private UserRepository userRepository;
    @Mock private FileService fileService;
    @Mock private StoragePort storagePort;
    @Mock private FileUrl fileUrl;

    private UserService userService;
    private User user;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, fileService, storagePort, fileUrl);
        ReflectionTestUtils.setField(userService, "maxProfileImageSize", 5L * 1024 * 1024);
        user = User.builder().id("user-1").email(EMAIL).profileImage("profiles/old.png").build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(storagePort.requiresDirectUpload()).thenReturn(true);
    }

    @Test
    @DisplayName("발급한 key는 사용자 소유 prefix를 가지며 완료 전 DB에 반영되지 않는다")
    void prepareProfileImageUpload_returnsOwnedKey() {
        PresignedUpload presigned = presigned();
        when(storagePort.presignPut(any(), eq("image/png"), eq(10L)))
                .thenReturn(Optional.of(presigned));
        when(fileUrl.of(any())).thenAnswer(invocation ->
                "https://cdn.example.test/" + invocation.<String>getArgument(0));

        ProfileImageUploadPreparation result = userService.prepareProfileImageUpload(
                EMAIL, "avatar.png", "image/png", 10L);

        assertThat(result.directUpload()).isTrue();
        assertThat(result.objectKey()).startsWith("profiles/user-1_");
        assertThat(result.imageUrl()).isEqualTo("https://cdn.example.test/" + result.objectKey());
        assertThat(user.getProfileImage()).isEqualTo("profiles/old.png");
    }

    @Test
    @DisplayName("PUT 완료 후 새 CloudFront URL을 저장하고 기존 객체를 삭제한다")
    void completeProfileImageUpload_updatesUserAfterPut() {
        String objectKey = "profiles/user-1_1700000000000_abcdef0123456789.png";
        when(fileUrl.of(objectKey)).thenReturn("https://cdn.example.test/" + objectKey);

        var response = userService.completeProfileImageUpload(EMAIL, objectKey);

        assertThat(user.getProfileImage()).isEqualTo(objectKey);
        assertThat(response.getImageUrl()).isEqualTo("https://cdn.example.test/" + objectKey);
        verify(userRepository).save(user);
        verify(storagePort).delete("profiles/old.png");
    }

    private PresignedUpload presigned() {
        return new PresignedUpload(
                URI.create("https://bucket.s3.amazonaws.com/profiles/avatar.png?sig=test"),
                Map.of("Content-Type", "image/png"),
                Instant.now().plusSeconds(300));
    }
}
