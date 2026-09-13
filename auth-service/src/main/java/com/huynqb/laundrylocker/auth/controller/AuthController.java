package com.huynqb.laundrylocker.auth.controller;

import com.huynqb.laundrylocker.auth.dto.*;
import com.huynqb.laundrylocker.auth.service.AuthService;
import com.huynqb.laundrylocker.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/api/auth/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok("AUTH_REGISTERED", "Account registered", authService.register(request));
    }

    @PostMapping("/api/auth/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok("AUTH_LOGIN_OK", "Login successful", authService.login(request));
    }

    @PostMapping("/api/auth/firebase")
    public ApiResponse<AuthResponse> firebaseLogin(@Valid @RequestBody FirebaseLoginRequest request) {
        return ApiResponse.ok("AUTH_LOGIN_OK", "Firebase login successful", authService.firebaseLogin(request.idToken()));
    }

    @PostMapping("/api/auth/refresh-token")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok("AUTH_REFRESH_OK", "Token refreshed", authService.refresh(request));
    }

    @PostMapping("/api/auth/phone-login")
    public ApiResponse<Map<String, Object>> phoneLogin(@RequestBody Map<String, Object> request) {
        return ApiResponse.ok("AUTH_PHONE_LOGIN_OK", "Phone login processed", authService.phoneLogin(request));
    }

    @PostMapping("/api/auth/complete-registration")
    public ApiResponse<AuthResponse> completeRegistration(@RequestBody Map<String, Object> request) {
        return ApiResponse.ok(
                "AUTH_REGISTRATION_COMPLETE",
                "Registration completed",
                authService.completeRegistration(request));
    }

    @PostMapping("/api/auth/kiosk/quick-register")
    public ApiResponse<AuthResponse> kioskQuickRegister(@RequestBody Map<String, Object> request) {
        return ApiResponse.ok(
                "AUTH_REGISTRATION_COMPLETE",
                "Kiosk registration completed",
                authService.kioskQuickRegister(request));
    }

    @PostMapping("/api/auth/email/send-otp")
    public ApiResponse<Void> sendEmailOtp(@RequestBody Map<String, Object> request) {
        authService.sendEmailOtp(request);
        return ApiResponse.ok("AUTH_OTP_SENT", "OTP sent");
    }

    @PostMapping("/api/auth/email/verify-otp")
    public ApiResponse<Map<String, Object>> verifyEmailOtp(@RequestBody Map<String, Object> request) {
        return ApiResponse.ok(
                "AUTH_EMAIL_VERIFY_OK", "Email OTP verified", authService.verifyEmailOtp(request));
    }

    @PostMapping("/api/auth/email/complete-registration")
    public ApiResponse<AuthResponse> emailCompleteRegistration(@RequestBody Map<String, Object> request) {
        return ApiResponse.ok(
                "AUTH_REGISTRATION_COMPLETE",
                "Email registration completed",
                authService.emailCompleteRegistration(request));
    }

    @PostMapping("/api/auth/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ApiResponse.ok("AUTH_LOGOUT_OK", "Logged out");
    }

    @PostMapping("/api/auth/forgot-password")
    public ApiResponse<Void> forgotPassword(@RequestBody Map<String, Object> request) {
        authService.sendPasswordResetOtp(request);
        return ApiResponse.ok("AUTH_OTP_SENT", "Password reset OTP sent");
    }

    @PostMapping("/api/auth/reset-password")
    public ApiResponse<Void> resetPassword(@RequestBody Map<String, Object> request) {
        authService.resetPassword(request);
        return ApiResponse.ok("PASSWORD_RESET_SUCCESS", "Password reset successful");
    }

    @PostMapping("/api/admin/auth/login")
    public ApiResponse<Map<String, Object>> adminLogin(@RequestBody Map<String, Object> request) {
        return ApiResponse.ok(
                "ADMIN_AUTH_2FA_REQUIRED", "Admin 2FA required", authService.adminLogin(request));
    }

    @PostMapping("/api/admin/auth/verify-2fa")
    public ApiResponse<Map<String, Object>> adminVerify2fa(@RequestBody Map<String, Object> request) {
        return ApiResponse.ok(
                "ADMIN_AUTH_2FA_SUCCESS", "Admin 2FA verified", authService.verifyAdmin2fa(request));
    }

    @PostMapping("/api/admin/auth/refresh")
    public ApiResponse<AuthResponse> adminRefresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok("AUTH_REFRESH_OK", "Admin token refreshed", authService.adminRefresh(request));
    }

    @GetMapping("/internal/auth/accounts/{id}")
    public ApiResponse<AuthResponse> getAccount(@PathVariable Long id) {
        return ApiResponse.ok(authService.getAccount(id));
    }

    @PostMapping("/internal/auth/accounts")
    public ApiResponse<AuthResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        return ApiResponse.ok("AUTH_ACCOUNT_CREATED", "Account created", authService.createAccount(request));
    }

    @GetMapping("/internal/auth/accounts/by-users")
    public ApiResponse<List<Map<String, Object>>> accountsByUsers(@RequestParam List<Long> userIds) {
        return ApiResponse.ok(authService.accountsByUsers(userIds));
    }

    @PostMapping("/internal/auth/users/{userId}/password")
    public ApiResponse<Void> changePassword(
            @PathVariable Long userId, @RequestBody Map<String, Object> request) {
        authService.changePassword(userId, request);
        return ApiResponse.ok("PASSWORD_CHANGED", "Password changed");
    }
}
