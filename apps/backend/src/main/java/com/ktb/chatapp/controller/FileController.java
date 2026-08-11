package com.ktb.chatapp.controller;

import com.ktb.chatapp.dto.StandardResponse;
import com.ktb.chatapp.dto.UploadUrlRequest;
import com.ktb.chatapp.model.File;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.service.FileAccess;
import com.ktb.chatapp.service.FileAccessService;
import com.ktb.chatapp.service.FileService;
import com.ktb.chatapp.service.FileUploadResult;
import com.ktb.chatapp.service.FileUploadPreparation;
import com.ktb.chatapp.service.FileUrl;
import com.ktb.chatapp.service.PreviewNotSupportedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "파일 (Files)", description = "파일 업로드 및 다운로드 API")
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;
    private final FileAccessService fileAccessService;
    private final UserRepository userRepository;
    private final FileUrl fileUrl;

    /**
     * S3 모드에서는 브라우저 직접 PUT용 URL을 발급한다. 로컬 모드에서는 directUpload=false를 내려 기존
     * multipart 업로드 경로를 사용하게 한다.
     */
    @Operation(summary = "파일 업로드 URL 발급", description = "S3 PUT용 Presigned URL을 발급합니다.")
    @PostMapping("/upload-url")
    public ResponseEntity<?> createUploadUrl(
            @Valid @RequestBody UploadUrlRequest request,
            Principal principal) {
        try {
            User user = findUser(principal);
            FileUploadPreparation preparation = fileService.prepareUpload(
                    request.filename(), request.contentType(), request.size(), user.getId());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("directUpload", preparation.directUpload());
            if (preparation.directUpload()) {
                response.put("uploadUrl", preparation.presignedUpload().url().toString());
                response.put("requiredHeaders", preparation.presignedUpload().requiredHeaders());
                response.put("expiresAt", preparation.presignedUpload().expiresAt());
                response.put("file", fileData(preparation.file()));
            }
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(StandardResponse.error(e.getMessage()));
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(StandardResponse.error("사용자를 찾을 수 없습니다."));
        } catch (Exception e) {
            log.error("파일 업로드 URL 발급 중 에러 발생", e);
            return ResponseEntity.internalServerError()
                    .body(StandardResponse.error("파일 업로드 URL 발급 중 오류가 발생했습니다."));
        }
    }

    /** S3 PUT이 성공한 뒤 pending 메타데이터를 채팅에 첨부 가능한 상태로 전환한다. */
    @Operation(summary = "파일 업로드 완료", description = "직접 업로드가 끝난 파일을 사용 가능한 상태로 확정합니다.")
    @PostMapping("/uploads/{id}/complete")
    public ResponseEntity<?> completeUpload(@PathVariable String id, Principal principal) {
        try {
            User user = findUser(principal);
            FileUploadResult result = fileService.completeUpload(id, user.getId());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "파일 업로드 성공");
            response.put("file", fileData(result.getFile()));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("파일 업로드 완료 처리 중 에러 발생: {}", id, e);
            return handleFileError(e);
        }
    }

    /**
     * 파일 업로드
     */
    @Operation(summary = "파일 업로드", description = "로컬 저장 모드에서 파일을 업로드합니다. 최대 5MB까지 가능합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "파일 업로드 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 파일",
            content = @Content(schema = @Schema(implementation = StandardResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 실패",
            content = @Content(schema = @Schema(implementation = StandardResponse.class))),
        @ApiResponse(responseCode = "413", description = "파일 크기 초과",
            content = @Content(schema = @Schema(implementation = StandardResponse.class))),
        @ApiResponse(responseCode = "500", description = "서버 내부 오류",
            content = @Content(schema = @Schema(implementation = StandardResponse.class)))
    })
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @Parameter(description = "업로드할 파일") @RequestParam("file") MultipartFile file,
            Principal principal) {
        try {
            User user = findUser(principal);

            FileUploadResult result = fileService.uploadFile(file, user.getId());

            if (result.isSuccess()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "파일 업로드 성공");
                
                response.put("file", fileData(result.getFile()));

                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "파일 업로드에 실패했습니다.");
                return ResponseEntity.status(500).body(errorResponse);
            }

        } catch (Exception e) {
            log.error("파일 업로드 중 에러 발생", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "파일 업로드 중 오류가 발생했습니다.");
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    private User findUser(Principal principal) {
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found: " + principal.getName()));
    }

    private Map<String, Object> fileData(File file) {
        Map<String, Object> data = new HashMap<>();
        data.put("_id", file.getId());
        data.put("filename", file.getFilename());
        data.put("originalname", file.getOriginalname());
        data.put("mimetype", file.getMimetype());
        data.put("size", file.getSize());
        data.put("uploadDate", file.getUploadDate());
        data.put("url", fileUrl.of(file.getPath()));
        return data;
    }

    /**
     * 보안이 강화된 파일 다운로드
     */
    @Operation(summary = "파일 다운로드", description = "업로드된 파일을 다운로드합니다. 본인이 업로드한 파일만 다운로드 가능합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "파일 다운로드 성공"),
        @ApiResponse(responseCode = "401", description = "인증 실패",
            content = @Content(schema = @Schema(implementation = StandardResponse.class))),
        @ApiResponse(responseCode = "403", description = "권한 없음",
            content = @Content(schema = @Schema(implementation = StandardResponse.class))),
        @ApiResponse(responseCode = "404", description = "파일을 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = StandardResponse.class))),
        @ApiResponse(responseCode = "500", description = "서버 내부 오류",
            content = @Content(schema = @Schema(implementation = StandardResponse.class)))
    })
    @GetMapping("/download/{filename:.+}")
    public ResponseEntity<?> downloadFile(
            @Parameter(description = "다운로드할 파일명") @PathVariable String filename,
            HttpServletRequest request,
            Principal principal) {
        try {
            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));

            return switch (fileAccessService.forDownload(filename, user.getId())) {
                case FileAccess.Stream stream -> attachmentResponse(stream);
                case FileAccess.Redirect redirect -> redirectResponse(redirect);
            };

        } catch (Exception e) {
            log.error("파일 다운로드 중 에러 발생: {}", filename, e);
            return handleFileError(e);
        }
    }

    private ResponseEntity<?> attachmentResponse(FileAccess.Stream stream) {
        String contentDisposition = String.format(
                "attachment; filename*=UTF-8''%s",
                encodeFilename(stream.originalname())
        );

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stream.contentType()))
                .contentLength(stream.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-cache, no-store, must-revalidate")
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Content-Disposition")
                .body(stream.resource());
    }

    private ResponseEntity<?> redirectResponse(FileAccess.Redirect redirect) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(redirect.location())
                .build();
    }

    private String encodeFilename(String originalFilename) {
        return URLEncoder.encode(originalFilename, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
    }

    private ResponseEntity<?> handleFileError(Exception e) {
        String errorMessage = e.getMessage();
        int statusCode = 500;
        String responseMessage = "파일 처리 중 오류가 발생했습니다.";

        if (errorMessage != null) {
            if (errorMessage.contains("잘못된 파일명") || errorMessage.contains("Invalid filename")) {
                statusCode = 400;
                responseMessage = "잘못된 파일명입니다.";
            } else if (errorMessage.contains("인증") || errorMessage.contains("Authentication")) {
                statusCode = 401;
                responseMessage = "인증이 필요합니다.";
            } else if (errorMessage.contains("잘못된 파일 경로") || errorMessage.contains("Invalid file path")) {
                statusCode = 400;
                responseMessage = "잘못된 파일 경로입니다.";
            } else if (errorMessage.contains("찾을 수 없습니다") || errorMessage.contains("not found")) {
                statusCode = 404;
                responseMessage = "파일을 찾을 수 없습니다.";
            } else if (errorMessage.contains("메시지를 찾을 수 없습니다")) {
                statusCode = 404;
                responseMessage = "파일 메시지를 찾을 수 없습니다.";
            } else if (errorMessage.contains("권한") || errorMessage.contains("Unauthorized")) {
                statusCode = 403;
                responseMessage = "파일에 접근할 권한이 없습니다.";
            }
        }

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("message", responseMessage);

        return ResponseEntity.status(statusCode).body(errorResponse);
    }

    @GetMapping("/view/{filename:.+}")
    public ResponseEntity<?> viewFile(
            @PathVariable String filename,
            HttpServletRequest request,
            Principal principal) {
        try {
            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));

            return switch (fileAccessService.forView(filename, user.getId())) {
                case FileAccess.Stream stream -> inlineResponse(stream);
                case FileAccess.Redirect redirect -> redirectResponse(redirect);
            };

        } catch (PreviewNotSupportedException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(415).body(errorResponse);

        } catch (Exception e) {
            log.error("파일 미리보기 중 에러 발생: {}", filename, e);
            return handleFileError(e);
        }
    }

    private ResponseEntity<?> inlineResponse(FileAccess.Stream stream) {
        String contentDisposition = String.format(
                "inline; filename=\"%s\"; filename*=UTF-8''%s",
                stream.originalname(),
                encodeFilename(stream.originalname())
        );

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stream.contentType()))
                .contentLength(stream.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(stream.resource());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFile(@PathVariable String id, Principal principal) {
        try {
            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));

            boolean deleted = fileService.deleteFile(id, user.getId());

            if (deleted) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "파일이 삭제되었습니다.");
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "파일 삭제에 실패했습니다.");
                return ResponseEntity.status(400).body(errorResponse);
            }

        } catch (RuntimeException e) {
            log.error("파일 삭제 중 에러 발생: {}", id, e);
            String errorMessage = e.getMessage();
            
            if (errorMessage != null && errorMessage.contains("찾을 수 없습니다")) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "파일을 찾을 수 없습니다.");
                return ResponseEntity.status(404).body(errorResponse);
            } else if (errorMessage != null && errorMessage.contains("권한")) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "파일을 삭제할 권한이 없습니다.");
                return ResponseEntity.status(403).body(errorResponse);
            }
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "파일 삭제 중 오류가 발생했습니다.");
            errorResponse.put("error", errorMessage);
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}
