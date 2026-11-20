package com.formasiserver.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;

import com.formasiserver.domain.auth.jwt.JwtProvider.IssuedToken;
import com.formasiserver.domain.auth.jwt.JwtProvider.VerifiedToken;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
class AuthTokenRedisRepositoryTest {

    @Autowired
    private AuthTokenRedisRepository store;

    @Autowired
    private StringRedisTemplate redis;

    private final UUID userId = UUID.randomUUID();
    private final UUID accessId = UUID.randomUUID();
    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private final Instant refreshExpiry = now.plusSeconds(600);
    private final String refreshKey = "auth:refresh:{" + userId + "}";
    private final String accessKey = "auth:access:block:{" + userId + "}:" + accessId;

    @AfterEach
    void removeTokenKeys() {
        redis.delete(refreshKey);
        redis.delete(accessKey);
    }

    @Test
    void storesOnlyCurrentRefreshHashWithTheAbsoluteExpiry() {
        store.saveRefresh(userId, new IssuedToken("abc", refreshExpiry));
        assertThat(redis.opsForValue().get(refreshKey))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad:"
                        + refreshExpiry.toEpochMilli());
        assertExpiry(refreshKey, refreshExpiry);

        Instant newLoginExpiry = refreshExpiry.plusSeconds(60);
        store.saveRefresh(userId, new IssuedToken("new-login", newLoginExpiry));
        assertThat(store.rotateRefresh(userId, "abc", new IssuedToken("obsolete", refreshExpiry))).isFalse();
        assertThat(store.revokeRefresh(userId, "abc")).isFalse();
        assertExpiry(refreshKey, newLoginExpiry);
        assertThat(store.revokeRefresh(userId, "new-login")).isTrue();
    }

    @Test
    void rotationPreservesExpiryAndRejectsThePreviousToken() {
        store.saveRefresh(userId, new IssuedToken("current", refreshExpiry));
        assertThat(store.rotateRefresh(userId, "current",
                new IssuedToken("longer", refreshExpiry.plusSeconds(1)))).isFalse();
        assertThat(store.rotateRefresh(userId, "current", new IssuedToken("replacement", refreshExpiry))).isTrue();
        assertExpiry(refreshKey, refreshExpiry);
        assertThat(store.rotateRefresh(userId, "current", new IssuedToken("stale", refreshExpiry))).isFalse();
        assertThat(store.revokeRefresh(userId, "replacement")).isTrue();
    }

    @Test
    void expiredRefreshCannotRotateOrOverwriteTheCurrentLogin() {
        store.saveRefresh(userId, new IssuedToken("original", refreshExpiry));
        assertThat(redis.expireAt(refreshKey, now.minusSeconds(1))).isTrue();
        assertThat(store.rotateRefresh(userId, "original", new IssuedToken("replacement", refreshExpiry))).isFalse();
        assertThat(store.revokeRefresh(userId, "original")).isFalse();

        store.saveRefresh(userId, new IssuedToken("new-login", refreshExpiry));
        assertThatIllegalStateException().isThrownBy(() ->
                store.saveRefresh(userId, new IssuedToken("expired", now.minusSeconds(1))));
        assertThat(store.revokeRefresh(userId, "new-login")).isTrue();
    }

    @Test
    void onlyOneConcurrentRotationCanUseTheSameRefresh() throws Exception {
        store.saveRefresh(userId, new IssuedToken("current", refreshExpiry));
        List<Boolean> results = race(
                () -> store.rotateRefresh(userId, "current", new IssuedToken("first", refreshExpiry)),
                () -> store.rotateRefresh(userId, "current", new IssuedToken("second", refreshExpiry)));

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertExpiry(refreshKey, refreshExpiry);
        assertThat(store.revokeRefresh(userId, results.getFirst() ? "first" : "second")).isTrue();
    }

    @Test
    void newLoginSurvivesConcurrentRotationOfThePreviousLogin() throws Exception {
        store.saveRefresh(userId, new IssuedToken("current", refreshExpiry));
        Instant newLoginExpiry = refreshExpiry.plusSeconds(60);
        race(
                () -> store.rotateRefresh(userId, "current", new IssuedToken("replacement", refreshExpiry)),
                () -> {
                    store.saveRefresh(userId, new IssuedToken("new-login", newLoginExpiry));
                    return true;
                });

        assertExpiry(refreshKey, newLoginExpiry);
        assertThat(store.revokeRefresh(userId, "new-login")).isTrue();
    }

    @Test
    void concurrentRotationAndLogoutCanOnlyConsumeTheCurrentRefreshOnce() throws Exception {
        store.saveRefresh(userId, new IssuedToken("current", refreshExpiry));
        VerifiedToken access = access(userId, now.plusSeconds(60));
        List<Boolean> results = race(
                () -> store.rotateRefresh(userId, "current", new IssuedToken("replacement", refreshExpiry)),
                () -> store.revokeRefreshAndBlockAccess(userId, "current", access));

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(store.isAccessBlocked(access)).isEqualTo(results.getLast());
        assertThat(store.revokeRefresh(userId, "replacement")).isEqualTo(results.getFirst());
    }

    @Test
    void newLoginSurvivesConcurrentLogoutOfThePreviousLogin() throws Exception {
        store.saveRefresh(userId, new IssuedToken("current", refreshExpiry));
        Instant newLoginExpiry = refreshExpiry.plusSeconds(60);
        VerifiedToken access = access(userId, now.plusSeconds(60));
        List<Boolean> results = race(
                () -> store.revokeRefreshAndBlockAccess(userId, "current", access),
                () -> {
                    store.saveRefresh(userId, new IssuedToken("new-login", newLoginExpiry));
                    return true;
                });

        assertThat(store.isAccessBlocked(access)).isEqualTo(results.getFirst());
        assertExpiry(refreshKey, newLoginExpiry);
        assertThat(store.revokeRefresh(userId, "new-login")).isTrue();
    }

    @Test
    void logoutOnlyBlocksTheSubmittedAccessUntilItsOwnExpiry() {
        store.saveRefresh(userId, new IssuedToken("current", refreshExpiry));
        VerifiedToken access = access(userId, now.plusSeconds(60));
        VerifiedToken otherAccess = new VerifiedToken(userId, UUID.randomUUID(), access.issuedAt(), access.expiresAt());

        assertThat(store.revokeRefreshAndBlockAccess(userId, "old", access)).isFalse();
        assertThat(store.isAccessBlocked(access)).isFalse();
        assertThat(store.revokeRefreshAndBlockAccess(userId, "current", access)).isTrue();
        assertThat(redis.hasKey(refreshKey)).isFalse();
        assertThat(store.isAccessBlocked(access)).isTrue();
        assertThat(store.isAccessBlocked(otherAccess)).isFalse();
        assertExpiry(accessKey, access.expiresAt());

        assertThat(store.revokeRefreshAndBlockAccess(userId, "current", access)).isFalse();
        assertExpiry(accessKey, access.expiresAt());
    }

    @Test
    void logoutStillRevokesRefreshWhenAccessHasExpiredOrBelongsToAnotherUser() {
        VerifiedToken expiredAccess = access(userId, now.minusSeconds(1));
        VerifiedToken anotherUsersAccess = access(UUID.randomUUID(), now.plusSeconds(60));
        store.saveRefresh(userId, new IssuedToken("current", refreshExpiry));
        assertThat(store.revokeRefreshAndBlockAccess(userId, "current", expiredAccess)).isTrue();
        assertThat(store.isAccessBlocked(expiredAccess)).isFalse();

        store.saveRefresh(userId, new IssuedToken("new-login", refreshExpiry));
        assertThat(store.revokeRefreshAndBlockAccess(userId, "new-login", anotherUsersAccess)).isTrue();
        assertThat(store.isAccessBlocked(anotherUsersAccess)).isFalse();
        assertThat(redis.hasKey(refreshKey)).isFalse();
    }

    @Test
    void redisFailuresPropagateDuringAuthenticationOperations() {
        StringRedisTemplate unavailable = mock(StringRedisTemplate.class, invocation -> {
            throw new RedisConnectionFailureException("Redis is unavailable");
        });
        AuthTokenRedisRepository unavailableStore = new AuthTokenRedisRepository(unavailable);
        IssuedToken refresh = new IssuedToken("replacement", refreshExpiry);
        VerifiedToken access = access(userId, now.plusSeconds(60));

        assertThatExceptionOfType(RedisConnectionFailureException.class)
                .isThrownBy(() -> unavailableStore.saveRefresh(userId, refresh));
        assertThatExceptionOfType(RedisConnectionFailureException.class)
                .isThrownBy(() -> unavailableStore.rotateRefresh(userId, "current", refresh));
        assertThatExceptionOfType(RedisConnectionFailureException.class)
                .isThrownBy(() -> unavailableStore.revokeRefresh(userId, "current"));
        assertThatExceptionOfType(RedisConnectionFailureException.class)
                .isThrownBy(() -> unavailableStore.revokeRefreshAndBlockAccess(userId, "current", access));
        assertThatExceptionOfType(RedisConnectionFailureException.class)
                .isThrownBy(() -> unavailableStore.isAccessBlocked(access));
    }

    private VerifiedToken access(UUID owner, Instant expiry) {
        return new VerifiedToken(owner, accessId, now.minusSeconds(120), expiry);
    }

    private void assertExpiry(String key, Instant expected) {
        long expectedMillis = expected.toEpochMilli();
        redis.execute((RedisCallback<Void>) connection -> {
            long before = connection.serverCommands().time(TimeUnit.MILLISECONDS);
            Long ttl = connection.keyCommands().pTtl(key.getBytes(StandardCharsets.UTF_8));
            long after = connection.serverCommands().time(TimeUnit.MILLISECONDS);
            assertThat(ttl).isPositive().isBetween(expectedMillis - after, expectedMillis - before);
            return null;
        });
    }

    private static List<Boolean> race(Callable<Boolean> first, Callable<Boolean> second) throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> firstResult = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return first.call();
            });
            Future<Boolean> secondResult = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return second.call();
            });
            return List.of(firstResult.get(10, TimeUnit.SECONDS), secondResult.get(10, TimeUnit.SECONDS));
        }
    }
}
