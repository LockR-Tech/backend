package com.huynqb.laundrylocker.auth.config;

import com.huynqb.laundrylocker.auth.dto.RegisterRequest;
import com.huynqb.laundrylocker.auth.repository.AuthAccountRepository;
import com.huynqb.laundrylocker.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * Seeds the first ADMIN account at startup so nobody has to poke SQL by hand:
 * public registration always forces CUSTOMER, and every other role is granted
 * through /api/admin/** — which needs an existing admin.
 * <p>
 * Enabled only when app.bootstrap.admin.email + password are set. Runs on a
 * background thread with retries because user-service (which stores the
 * profile/roles) may register with Eureka after auth-service starts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private static final int MAX_ATTEMPTS = 20;
    private static final long RETRY_DELAY_MS = 15_000;

    private final AuthAccountRepository authAccountRepository;
    private final AuthService authService;

    @Value("${app.bootstrap.admin.email:}")
    private String email;

    @Value("${app.bootstrap.admin.password:}")
    private String password;

    @Value("${app.bootstrap.admin.phone:}")
    private String phone;

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            log.info("Admin bootstrap disabled (set APP_BOOTSTRAP_ADMIN_EMAIL/PASSWORD to enable)");
            return;
        }
        if (authAccountRepository.findByEmail(email).isPresent()) {
            log.info("Admin bootstrap: account {} already exists", email);
            return;
        }
        Thread seeder = new Thread(this::seedWithRetries, "admin-bootstrap");
        seeder.setDaemon(true);
        seeder.start();
    }

    private void seedWithRetries() {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                if (authAccountRepository.findByEmail(email).isPresent()) {
                    return;
                }
                authService.provisionWithRoles(
                        new RegisterRequest(
                                null,
                                email,
                                StringUtils.hasText(phone) ? phone : null,
                                "System",
                                "Admin",
                                Set.of("ADMIN"),
                                password));
                log.warn(
                        "Bootstrap ADMIN account created for {} — change this password after first login",
                        email);
                return;
            } catch (Exception ex) {
                log.warn(
                        "Admin bootstrap attempt {}/{} failed ({}); retrying in {}s",
                        attempt,
                        MAX_ATTEMPTS,
                        ex.getMessage(),
                        RETRY_DELAY_MS / 1000);
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        log.error("Admin bootstrap gave up after {} attempts", MAX_ATTEMPTS);
    }
}
