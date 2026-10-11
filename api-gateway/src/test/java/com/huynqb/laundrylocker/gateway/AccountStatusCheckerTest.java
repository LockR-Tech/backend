package com.huynqb.laundrylocker.gateway;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class AccountStatusCheckerTest {

    private final MutableClock clock = new MutableClock();

    @Test
    void cachesResultWithinTtlThenAsksAgain() {
        AtomicInteger calls = new AtomicInteger();
        AccountStatusChecker checker = checker(id -> {
            calls.incrementAndGet();
            return Mono.just(calls.get() == 1);
        });

        assertTrue(checker.isActive(7L).block());
        clock.advance(Duration.ofSeconds(29));
        assertTrue(checker.isActive(7L).block());
        assertEquals(1, calls.get());

        clock.advance(Duration.ofSeconds(2));
        assertFalse(checker.isActive(7L).block());
        assertEquals(2, calls.get());
    }

    @Test
    void allowsWhenAuthServiceFailsOrIsSlow() {
        assertTrue(checker(id -> Mono.error(new IllegalStateException("down"))).isActive(7L).block());
        assertTrue(checker(id -> Mono.never()).isActive(7L).block());
        assertTrue(checker(id -> Mono.empty()).isActive(7L).block());
    }

    @Test
    void doesNotCacheFailures() {
        AtomicInteger calls = new AtomicInteger();
        AccountStatusChecker checker = checker(id -> calls.incrementAndGet() == 1
                ? Mono.error(new IllegalStateException("down"))
                : Mono.just(false));

        assertTrue(checker.isActive(7L).block());
        assertFalse(checker.isActive(7L).block());
    }

    @Test
    void skipsLookupWhenDisabledOrTokenHasNoAccountId() {
        AtomicInteger calls = new AtomicInteger();
        Function<Long, Mono<Boolean>> lookup = id -> {
            calls.incrementAndGet();
            return Mono.just(false);
        };
        AccountStatusChecker disabled =
                new AccountStatusChecker(lookup, false, Duration.ofSeconds(30), Duration.ofSeconds(1), clock);

        assertTrue(disabled.isActive(7L).block());
        assertTrue(checker(lookup).isActive(null).block());
        assertEquals(0, calls.get());
    }

    @Test
    void readsActiveFlagFromApiResponse() {
        assertFalse(AccountStatusChecker.readActive(Map.of("data", Map.of("accountId", 7, "active", false))));
        assertTrue(AccountStatusChecker.readActive(Map.of("data", Map.of("accountId", 7, "active", true))));
        assertTrue(AccountStatusChecker.readActive(Map.of("code", "X")));
        assertTrue(AccountStatusChecker.readActive(null));
    }

    private AccountStatusChecker checker(Function<Long, Mono<Boolean>> lookup) {
        return new AccountStatusChecker(lookup, true, Duration.ofSeconds(30), Duration.ofMillis(200), clock);
    }

    private static final class MutableClock extends Clock {
        private final AtomicLong millis = new AtomicLong(Instant.parse("2026-10-11T00:00:00Z").toEpochMilli());

        void advance(Duration duration) {
            millis.addAndGet(duration.toMillis());
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis.get());
        }

        @Override
        public long millis() {
            return millis.get();
        }
    }
}
