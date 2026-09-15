package com.huynqb.laundrylocker.common.media;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// Lưu ảnh trên Cloudinary theo mô hình "signed direct upload" (ADR-0004):
/// server chỉ ký tham số upload và xác minh chữ ký phản hồi, byte ảnh đi thẳng
/// từ client tới Cloudinary. Không cấu hình `CLOUDINARY_URL` ⇒ tắt, mọi thao tác
/// cần ảnh trả `503 MEDIA_STORAGE_DISABLED`.
@Slf4j
@Component
public class CloudinaryMediaStorage {

    public static final List<String> ALLOWED_FORMATS = List.of("jpg", "jpeg", "png", "webp", "heic", "heif");
    public static final long MAX_BYTES = 10L * 1024 * 1024;
    public static final int MAX_SIGNATURES_PER_REQUEST = 10;

    /// Cloudinary từ chối chữ ký upload cũ hơn 1 giờ.
    private static final Duration SIGNATURE_TTL = Duration.ofHours(1);
    private static final String THUMBNAIL_TRANSFORMATION = "c_fill,w_320,h_320/f_auto,q_auto";
    private static final Pattern PUBLIC_ID_PATTERN = Pattern.compile("[A-Za-z0-9_\\-/]{1,255}");
    private static final Pattern FOLDER_ROOT_PATTERN = Pattern.compile("[A-Za-z0-9_\\-]+(/[A-Za-z0-9_\\-]+)*");

    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final String folderRoot;
    private final Clock clock;
    private final HttpClient httpClient;

    @Autowired
    public CloudinaryMediaStorage(
            @Value("${app.media.cloudinary-url:${CLOUDINARY_URL:}}") String cloudinaryUrl,
            @Value("${app.media.folder-root:${MEDIA_FOLDER_ROOT:lockr}}") String folderRoot) {
        this(cloudinaryUrl, folderRoot, Clock.systemUTC(), null);
    }

    CloudinaryMediaStorage(String cloudinaryUrl, String folderRoot, Clock clock, HttpClient httpClient) {
        String root = folderRoot == null ? "" : folderRoot.trim().replaceAll("^/+|/+$", "");
        if (!FOLDER_ROOT_PATTERN.matcher(root).matches()) {
            throw new IllegalStateException("app.media.folder-root must be letters, digits, '_', '-' or '/'");
        }
        this.folderRoot = root;
        this.clock = clock;
        String[] credentials = parseCloudinaryUrl(cloudinaryUrl);
        this.apiKey = credentials[0];
        this.apiSecret = credentials[1];
        this.cloudName = credentials[2];
        this.httpClient = httpClient != null
                ? httpClient
                : isEnabled() ? HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build() : null;
        if (isEnabled()) {
            log.info("Cloudinary media storage enabled (cloud={}, root={})", cloudName, this.folderRoot);
        }
    }

    public boolean isEnabled() {
        return cloudName != null;
    }

    public String cloudName() {
        return cloudName;
    }

    public UploadSignatureResponse createUploadSignatures(MediaPurpose purpose, Long userId, int count) {
        requireEnabled();
        requireUser(userId);
        if (count < 1 || count > MAX_SIGNATURES_PER_REQUEST) {
            throw new BusinessException(
                    "MEDIA_COUNT_INVALID", "count must be between 1 and " + MAX_SIGNATURES_PER_REQUEST);
        }
        long timestamp = clock.instant().getEpochSecond();
        String allowedFormats = String.join(",", ALLOWED_FORMATS);
        List<UploadSignatureResponse.SignedUpload> uploads = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String publicId = ownerPrefix(purpose, userId) + UUID.randomUUID().toString().replace("-", "");
            Map<String, String> signed = new LinkedHashMap<>();
            signed.put("timestamp", String.valueOf(timestamp));
            signed.put("public_id", publicId);
            signed.put("allowed_formats", allowedFormats);
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("api_key", apiKey);
            fields.putAll(signed);
            fields.put("signature", sign(signed, apiSecret));
            uploads.add(new UploadSignatureResponse.SignedUpload(publicId, fields));
        }
        return new UploadSignatureResponse(
                "CLOUDINARY",
                cloudName,
                "https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload",
                MAX_BYTES,
                ALLOWED_FORMATS,
                clock.instant().plus(SIGNATURE_TTL),
                uploads);
    }

    /// Xác minh ảnh do chính `actorUserId` upload với đúng `purpose`: chữ ký phản hồi
    /// Cloudinary (`sha1(public_id=..&version=.. + secret)`) + tiền tố thư mục chủ sở hữu.
    public VerifiedMedia verify(MediaUpload upload, MediaPurpose purpose, Long actorUserId) {
        requireEnabled();
        requireUser(actorUserId);
        if (upload == null
                || upload.publicId() == null
                || upload.version() == null
                || upload.signature() == null
                || upload.signature().isBlank()
                || !PUBLIC_ID_PATTERN.matcher(upload.publicId()).matches()
                || upload.publicId().contains("//")) {
            throw new BusinessException(
                    "MEDIA_UPLOAD_INVALID", "publicId, version and signature from Cloudinary are required");
        }
        if (!upload.publicId().startsWith(ownerPrefix(purpose, actorUserId))) {
            throw new BusinessException(
                    "MEDIA_OWNER_MISMATCH", "Image was not uploaded by this user for " + purpose.name());
        }
        String expected = sign(
                Map.of("public_id", upload.publicId(), "version", String.valueOf(upload.version())), apiSecret);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                upload.signature().trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII))) {
            throw new BusinessException("MEDIA_SIGNATURE_INVALID", "Cloudinary upload signature is invalid");
        }
        String format = upload.format() == null ? "" : upload.format().trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_FORMATS.contains(format)) {
            throw new BusinessException(
                    "MEDIA_FORMAT_INVALID", "Allowed image formats: " + String.join(", ", ALLOWED_FORMATS));
        }
        String secureUrl = "https://res.cloudinary.com/" + cloudName + "/image/upload/v"
                + upload.version() + "/" + upload.publicId() + "." + format;
        return new VerifiedMedia(
                upload.publicId(), secureUrl, format, upload.bytes(), upload.width(), upload.height());
    }

    /// Ảnh thu nhỏ vuông 320px cho danh sách; URL không phải của Cloudinary hệ thống thì giữ nguyên.
    public String thumbnailUrl(String secureUrl) {
        if (!isEnabled() || secureUrl == null) {
            return secureUrl;
        }
        String marker = "https://res.cloudinary.com/" + cloudName + "/image/upload/";
        if (!secureUrl.startsWith(marker)) {
            return secureUrl;
        }
        return marker + THUMBNAIL_TRANSFORMATION + "/" + secureUrl.substring(marker.length());
    }

    /// `public_id` của một URL nếu URL đó là ảnh nằm trong thư mục của hệ thống —
    /// dùng để dọn ảnh cũ khi thay ảnh đại diện/cửa hàng/khuyến mãi.
    public Optional<String> ownedPublicId(String url) {
        if (!isEnabled() || url == null) {
            return Optional.empty();
        }
        Pattern pattern = Pattern.compile(
                "^https://res\\.cloudinary\\.com/" + Pattern.quote(cloudName)
                        + "/image/upload/(?:v\\d+/)?(" + Pattern.quote(folderRoot) + "/[A-Za-z0-9_\\-/]+)\\.[A-Za-z0-9]+$");
        Matcher matcher = pattern.matcher(url.trim());
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    /// Xoá ảnh khi transaction hiện tại commit thành công (rollback ⇒ giữ ảnh vì DB vẫn trỏ tới nó).
    public void deleteAfterCommit(String publicId) {
        if (!isEnabled() || publicId == null) {
            return;
        }
        AfterCommit.run(() -> deleteQuietly(publicId));
    }

    /// Tách riêng để lớp ngoài không bắt buộc có spring-tx lúc nạp class.
    private static final class AfterCommit {
        static void run(Runnable action) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                });
            } else {
                action.run();
            }
        }
    }

    /// Dọn ảnh cũ sau khi thay ảnh đại diện/cửa hàng/khuyến mãi — chỉ khi ảnh cũ thuộc thư mục hệ thống.
    public void deleteReplacedAfterCommit(String oldUrl, String newUrl) {
        if (oldUrl != null && !oldUrl.equals(newUrl)) {
            ownedPublicId(oldUrl).ifPresent(this::deleteAfterCommit);
        }
    }

    /// Xoá ảnh trên Cloudinary, bất đồng bộ và không bao giờ ném lỗi — DB là nguồn sự thật,
    /// ảnh mồ côi trên Cloudinary chỉ tốn dung lượng.
    public void deleteQuietly(String publicId) {
        if (!isEnabled() || publicId == null || !publicId.startsWith(folderRoot + "/")) {
            return;
        }
        Map<String, String> signed = new TreeMap<>();
        signed.put("public_id", publicId);
        signed.put("invalidate", "true");
        signed.put("timestamp", String.valueOf(clock.instant().getEpochSecond()));
        Map<String, String> form = new LinkedHashMap<>(signed);
        form.put("api_key", apiKey);
        form.put("signature", sign(signed, apiSecret));
        String body = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("https://api.cloudinary.com/v1_1/" + cloudName + "/image/destroy"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .whenComplete((response, error) -> {
                        if (error != null) {
                            log.warn("Could not delete Cloudinary image {}: {}", publicId, error.getMessage());
                        } else if (response.statusCode() >= 300) {
                            log.warn("Cloudinary refused to delete {}: HTTP {} {}",
                                    publicId, response.statusCode(), response.body());
                        }
                    });
        } catch (RuntimeException ex) {
            log.warn("Could not delete Cloudinary image {}: {}", publicId, ex.getMessage());
        }
    }

    /// Thuật toán ký của Cloudinary: tham số sắp theo tên, bỏ giá trị rỗng,
    /// nối `k=v` bằng `&`, thêm api_secret, SHA-1 hex.
    public static String sign(Map<String, String> params, String secret) {
        String payload = new TreeMap<>(params).entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest((payload + secret).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 is not available", ex);
        }
    }

    private String ownerPrefix(MediaPurpose purpose, Long userId) {
        return folderRoot + "/" + purpose.folder() + "/u" + userId + "/";
    }

    private void requireEnabled() {
        if (!isEnabled()) {
            throw new BusinessException(
                    "MEDIA_STORAGE_DISABLED",
                    "Image storage is not configured (CLOUDINARY_URL)",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private static void requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("UNAUTHORIZED", "A signed-in user is required", HttpStatus.UNAUTHORIZED);
        }
    }

    /// `cloudinary://<api_key>:<api_secret>@<cloud_name>` → [key, secret, cloud]; rỗng ⇒ tắt.
    private static String[] parseCloudinaryUrl(String value) {
        if (value == null || value.isBlank()) {
            return new String[3];
        }
        try {
            URI uri = URI.create(value.trim());
            String userInfo = uri.getRawUserInfo();
            int colon = userInfo == null ? -1 : userInfo.indexOf(':');
            if (!"cloudinary".equals(uri.getScheme()) || colon <= 0 || uri.getHost() == null) {
                throw new IllegalArgumentException("expected cloudinary://<api_key>:<api_secret>@<cloud_name>");
            }
            return new String[] {
                    URLDecoder.decode(userInfo.substring(0, colon), StandardCharsets.UTF_8),
                    URLDecoder.decode(userInfo.substring(colon + 1), StandardCharsets.UTF_8),
                    uri.getHost()
            };
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("CLOUDINARY_URL is malformed: " + ex.getMessage(), ex);
        }
    }
}
