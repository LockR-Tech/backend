package com.huynqb.laundrylocker.common.media;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CloudinaryMediaStorageTest {

    private static final String SECRET = "test-secret";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-15T08:00:00Z"), ZoneOffset.UTC);

    private final CloudinaryMediaStorage storage =
            new CloudinaryMediaStorage("cloudinary://123456:" + SECRET + "@lockr-cloud", "lockr-test", CLOCK, null);

    @Test
    void signMatchesCloudinaryDocumentationExample() {
        String signature = CloudinaryMediaStorage.sign(
                Map.of(
                        "eager", "w_400,h_300,c_pad|w_260,h_200,c_crop",
                        "public_id", "sample_image",
                        "timestamp", "1315060510"),
                "abcd");
        assertEquals("bfd09f95f331f558cbd1320e67aa8d488770583e", signature);
    }

    @Test
    void disabledWithoutCloudinaryUrl() {
        CloudinaryMediaStorage disabled = new CloudinaryMediaStorage("", "lockr", CLOCK, null);
        assertFalse(disabled.isEnabled());
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> disabled.createUploadSignatures(MediaPurpose.AVATAR, 1L, 1));
        assertEquals("MEDIA_STORAGE_DISABLED", ex.getCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }

    @Test
    void rejectsMalformedCloudinaryUrl() {
        assertThrows(
                IllegalStateException.class,
                () -> new CloudinaryMediaStorage("https://example.com", "lockr", CLOCK, null));
    }

    @Test
    void createsSignedUploadsScopedToUserFolder() {
        UploadSignatureResponse response = storage.createUploadSignatures(MediaPurpose.REPORT_EVIDENCE, 42L, 2);

        assertEquals("https://api.cloudinary.com/v1_1/lockr-cloud/image/upload", response.uploadUrl());
        assertEquals(Instant.parse("2026-09-15T09:00:00Z"), response.expiresAt());
        assertEquals(2, response.uploads().size());
        assertNotEquals(response.uploads().get(0).publicId(), response.uploads().get(1).publicId());

        UploadSignatureResponse.SignedUpload upload = response.uploads().get(0);
        assertTrue(upload.publicId().startsWith("lockr-test/reports/u42/"));
        Map<String, String> fields = upload.fields();
        assertEquals("123456", fields.get("api_key"));
        assertEquals(upload.publicId(), fields.get("public_id"));
        assertEquals("1789459200", fields.get("timestamp"));
        String expected = CloudinaryMediaStorage.sign(
                Map.of(
                        "timestamp", fields.get("timestamp"),
                        "public_id", fields.get("public_id"),
                        "allowed_formats", fields.get("allowed_formats")),
                SECRET);
        assertEquals(expected, fields.get("signature"));
    }

    @Test
    void rejectsSignatureCountOutOfRange() {
        assertThrows(
                BusinessException.class,
                () -> storage.createUploadSignatures(MediaPurpose.AVATAR, 1L, 11));
    }

    @Test
    void verifyAcceptsGenuineUploadAndBuildsUrlServerSide() {
        String publicId = "lockr-test/reports/u42/abc123";
        MediaUpload upload = new MediaUpload(
                publicId, 1726390012L, responseSignature(publicId, 1726390012L), "JPG", 1000L, 800, 600);

        VerifiedMedia media = storage.verify(upload, MediaPurpose.REPORT_EVIDENCE, 42L);

        assertEquals(
                "https://res.cloudinary.com/lockr-cloud/image/upload/v1726390012/lockr-test/reports/u42/abc123.jpg",
                media.secureUrl());
        assertEquals("jpg", media.format());
        assertEquals(800, media.width());
    }

    @Test
    void verifyRejectsForgedSignature() {
        String publicId = "lockr-test/reports/u42/abc123";
        MediaUpload upload = new MediaUpload(publicId, 1L, "0000", "jpg", 1L, 1, 1);
        BusinessException ex = assertThrows(
                BusinessException.class, () -> storage.verify(upload, MediaPurpose.REPORT_EVIDENCE, 42L));
        assertEquals("MEDIA_SIGNATURE_INVALID", ex.getCode());
    }

    @Test
    void verifyRejectsImageUploadedByAnotherUserOrPurpose() {
        String publicId = "lockr-test/reports/u7/abc123";
        MediaUpload upload = new MediaUpload(publicId, 1L, responseSignature(publicId, 1L), "jpg", 1L, 1, 1);

        assertEquals("MEDIA_OWNER_MISMATCH", assertThrows(
                BusinessException.class,
                () -> storage.verify(upload, MediaPurpose.REPORT_EVIDENCE, 42L)).getCode());
        assertEquals("MEDIA_OWNER_MISMATCH", assertThrows(
                BusinessException.class,
                () -> storage.verify(upload, MediaPurpose.AVATAR, 7L)).getCode());
    }

    @Test
    void verifyRejectsUnsupportedFormat() {
        String publicId = "lockr-test/avatars/u1/x";
        MediaUpload upload = new MediaUpload(publicId, 1L, responseSignature(publicId, 1L), "svg", 1L, 1, 1);
        assertEquals("MEDIA_FORMAT_INVALID", assertThrows(
                BusinessException.class,
                () -> storage.verify(upload, MediaPurpose.AVATAR, 1L)).getCode());
    }

    @Test
    void thumbnailAndOwnedPublicIdOnlyApplyToOwnCloud() {
        String url = "https://res.cloudinary.com/lockr-cloud/image/upload/v5/lockr-test/stores/u1/abc.png";

        assertEquals(
                "https://res.cloudinary.com/lockr-cloud/image/upload/c_fill,w_320,h_320/f_auto,q_auto/v5/lockr-test/stores/u1/abc.png",
                storage.thumbnailUrl(url));
        assertEquals("lockr-test/stores/u1/abc", storage.ownedPublicId(url).orElseThrow());

        String foreign = "https://images.unsplash.com/photo-1.jpg";
        assertEquals(foreign, storage.thumbnailUrl(foreign));
        assertTrue(storage.ownedPublicId(foreign).isEmpty());
        assertTrue(storage.ownedPublicId(
                "https://res.cloudinary.com/lockr-cloud/image/upload/v5/other-root/x.png").isEmpty());
    }

    @Test
    void purposeRoleRules() {
        assertTrue(MediaPurpose.REPORT_EVIDENCE.isAllowedFor(List.of("CUSTOMER")));
        assertFalse(MediaPurpose.STORE_IMAGE.isAllowedFor(List.of("TECHNICIAN")));
        assertTrue(MediaPurpose.PROMOTION_IMAGE.isAllowedFor(List.of("ADMIN")));
        assertEquals(MediaPurpose.AVATAR, MediaPurpose.parse(" avatar "));
        assertThrows(BusinessException.class, () -> MediaPurpose.parse("VIDEO"));
    }

    private static String responseSignature(String publicId, long version) {
        return CloudinaryMediaStorage.sign(
                Map.of("public_id", publicId, "version", String.valueOf(version)), SECRET);
    }
}
