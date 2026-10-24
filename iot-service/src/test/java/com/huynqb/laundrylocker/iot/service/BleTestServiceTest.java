package com.huynqb.laundrylocker.iot.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.iot.client.LockerClient;
import com.huynqb.laundrylocker.iot.dto.BleOpenTokenResponse;
import com.huynqb.laundrylocker.iot.dto.LockerLayoutView;
import com.huynqb.laundrylocker.iot.dto.LockerView;
import com.huynqb.laundrylocker.iot.model.BleSigningKey;
import com.huynqb.laundrylocker.iot.model.BleTestGrant;
import com.huynqb.laundrylocker.iot.repository.BleSigningKeyRepository;
import com.huynqb.laundrylocker.iot.repository.BleTestGrantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/// Thử mở ô qua Bluetooth (F3-G11): chỉ tài khoản admin đã cấp mới xin được mã; mã đúng định dạng 91 byte
/// mà Pi giải và chữ ký kiểm được bằng khoá công khai Pi tải về.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BleTestServiceTest {

    static final long LOCKER = 7L, CUSTOMER = 1234L, ADMIN = 1L;

    @Mock BleTestGrantRepository grants;
    @Mock BleSigningKeyRepository keys;
    @Mock LockerClient lockerClient;
    @Mock CabinetLayoutLookup layoutLookup;
    @Mock CabinetControlLogService logs;
    BleTokenSigner signer;
    BleTestService service;

    @BeforeEach
    void setUp() {
        AtomicReference<BleSigningKey> stored = new AtomicReference<>();
        when(keys.findById(BleSigningKey.SINGLETON_ID)).thenAnswer(i -> Optional.ofNullable(stored.get()));
        when(keys.saveAndFlush(any())).thenAnswer(i -> {
            stored.set(i.getArgument(0));
            return i.getArgument(0);
        });
        signer = new BleTokenSigner(keys);
        service = new BleTestService(grants, signer, lockerClient, layoutLookup, logs);
        when(lockerClient.lockers(List.of(LOCKER))).thenReturn(
                ApiResponse.ok(List.of(new LockerView(LOCKER, "CAB-TU01", "Tủ thật TU01", null))));
        when(layoutLookup.layout(LOCKER)).thenReturn(Optional.of(new LockerLayoutView(LOCKER, "CAB-TU01", "Tủ thật",
                List.of(new LockerLayoutView.Cell(40L, 3, 1, 2, "OCCUPIED"),
                        new LockerLayoutView.Cell(41L, 4, 2, 1, "AVAILABLE")))));
    }

    @Test
    void customerWithoutGrantGetsNoToken() {
        when(grants.existsByLockerIdAndUserId(LOCKER, CUSTOMER)).thenReturn(false);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.issueToken(LOCKER, 3, CUSTOMER));
        assertEquals("BLE_TEST_NOT_GRANTED", ex.getCode());
        verifyNoInteractions(logs);
    }

    @Test
    void anonymousCallerIsRejected() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.issueToken(LOCKER, 3, null));
        assertEquals("UNAUTHORIZED", ex.getCode());
    }

    @Test
    void unknownBoxIsRejected() {
        when(grants.existsByLockerIdAndUserId(LOCKER, CUSTOMER)).thenReturn(true);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.issueToken(LOCKER, 9, CUSTOMER));
        assertEquals("BOX_NOT_IN_LOCKER", ex.getCode());
    }

    @Test
    void grantedCustomerGetsSignedTokenForAnyBoxEvenOccupied() throws Exception {
        when(grants.existsByLockerIdAndUserId(LOCKER, CUSTOMER)).thenReturn(true);
        long before = Instant.now().getEpochSecond();

        BleOpenTokenResponse r = service.issueToken(LOCKER, 3, CUSTOMER);

        assertEquals("OCCUPIED", r.boxStatus());
        assertEquals(40L, r.boxId());
        assertEquals("LOCKR_CAB-TU01", r.deviceName());
        assertEquals(BleTestService.SERVICE_UUID, r.serviceUuid());
        byte[] token = Base64.getUrlDecoder().decode(r.token());
        assertEquals(BleTokenSigner.PAYLOAD_LENGTH + BleTokenSigner.SIGNATURE_LENGTH, token.length);

        ByteBuffer b = ByteBuffer.wrap(token);
        assertEquals(1, b.get());
        assertEquals(LOCKER, Integer.toUnsignedLong(b.getInt()));
        assertEquals(3, Short.toUnsignedInt(b.getShort()));
        assertEquals(CUSTOMER, b.getLong());
        long exp = Integer.toUnsignedLong(b.getInt());
        assertTrue(exp >= before + BleTestService.TOKEN_TTL_SECONDS && exp <= before + BleTestService.TOKEN_TTL_SECONDS + 5);
        byte[] nonce = new byte[8];
        b.get(nonce);
        assertEquals(r.nonce(), HexFormat.of().formatHex(nonce));

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(rawPublicKey(Base64.getDecoder().decode(signer.publicKeyBase64())));
        verifier.update(token, 0, BleTokenSigner.PAYLOAD_LENGTH);
        assertTrue(verifier.verify(Arrays.copyOfRange(token, BleTokenSigner.PAYLOAD_LENGTH, token.length)));

        verify(logs).recordWeb(eq(LOCKER), eq(CUSTOMER), eq("BLE_TOKEN"), isNull(), eq(40L), isNull(), eq("ISSUED"),
                contains("ô 3"));
    }

    @Test
    void eachTokenHasItsOwnNonceAndKeyIsGeneratedOnce() {
        when(grants.existsByLockerIdAndUserId(LOCKER, CUSTOMER)).thenReturn(true);
        String a = service.issueToken(LOCKER, 4, CUSTOMER).nonce();
        String b = service.issueToken(LOCKER, 4, CUSTOMER).nonce();
        assertNotEquals(a, b);
        verify(keys, times(1)).saveAndFlush(any());
    }

    @Test
    void grantIsIdempotentAndListsLockersOfCustomer() {
        BleTestGrant existing = new BleTestGrant();
        existing.setLockerId(LOCKER);
        existing.setUserId(CUSTOMER);
        when(grants.findByLockerIdAndUserId(LOCKER, CUSTOMER)).thenReturn(Optional.of(existing));
        service.grant(LOCKER, CUSTOMER, ADMIN);
        verify(grants, never()).save(any());

        when(grants.findByUserIdOrderByLockerIdAsc(CUSTOMER)).thenReturn(List.of(existing));
        var lockers = service.lockersOf(CUSTOMER);
        assertEquals(1, lockers.size());
        assertEquals("CAB-TU01", lockers.get(0).code());
    }

    @Test
    void grantOnUnknownLockerIs404() {
        when(lockerClient.lockers(List.of(99L))).thenReturn(ApiResponse.ok(List.of()));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.grant(99L, CUSTOMER, ADMIN));
        assertEquals("LOCKER_NOT_FOUND", ex.getCode());
    }

    /// Khoá thô 32 byte → X.509 như Pi làm với `Ed25519PublicKey.from_public_bytes`.
    private static PublicKey rawPublicKey(byte[] raw) throws Exception {
        byte[] prefix = HexFormat.of().parseHex("302a300506032b6570032100");
        byte[] x509 = Arrays.copyOf(prefix, prefix.length + raw.length);
        System.arraycopy(raw, 0, x509, prefix.length, raw.length);
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(x509));
    }
}
