package com.formasiserver.domain.auth.repository;

import com.formasiserver.domain.auth.jwt.JwtProvider.IssuedToken;
import com.formasiserver.domain.auth.jwt.JwtProvider.VerifiedToken;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.SetCondition;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthTokenRedisRepository {

    private final StringRedisTemplate redis;

    public void saveRefresh(UUID userId, IssuedToken refresh) {
        Instant expiresAt = refresh.expiresAt();
        if (!expiresAt.isAfter(Instant.now())) {
            throw new IllegalStateException("Refresh Token could not be stored before expiry");
        }
        redis.opsForValue().set(refreshKey(userId), refreshValue(hash(refresh.value()), expiresAt),
                expirationAt(expiresAt));
    }

    public boolean isCurrentRefresh(VerifiedToken refresh, String token) {
        String expected = refreshValue(hash(token), refresh.expiresAt());
        return expected.equals(redis.opsForValue().get(refreshKey(refresh.userId())));
    }

    public boolean rotateRefresh(UUID userId, String currentRefresh, IssuedToken replacement) {
        String currentHash = hash(currentRefresh);
        String replacementHash = hash(replacement.value());
        if (currentHash.equals(replacementHash)) {
            throw new IllegalArgumentException("Replacement Refresh Token must be different");
        }
        String expected = refreshValue(currentHash, replacement.expiresAt());
        byte[] next = bytes(refreshValue(replacementHash, replacement.expiresAt()));
        Object result = changeRefresh(userId, expected::equals, (connection, key) ->
                connection.stringCommands().set(key, next, SetCondition.ifPresent(), Expiration.keepTtl()));
        return Boolean.TRUE.equals(result);
    }

    public boolean revokeRefresh(UUID userId, String currentRefresh) {
        return revokeRefresh(userId, currentRefresh, null);
    }

    public boolean revokeRefreshAndBlockAccess(UUID userId, String currentRefresh, VerifiedToken access) {
        return revokeRefresh(userId, currentRefresh, userId.equals(access.userId()) ? access : null);
    }

    public boolean isAccessBlocked(VerifiedToken access) {
        return Boolean.TRUE.equals(redis.hasKey(accessBlockKey(access)));
    }

    private boolean revokeRefresh(UUID userId, String currentRefresh, @Nullable VerifiedToken access) {
        String expectedPrefix = hash(currentRefresh) + ":";
        Object result = changeRefresh(userId, value -> value.startsWith(expectedPrefix), (connection, key) -> {
            if (access != null && access.expiresAt().isAfter(Instant.now())) {
                connection.stringCommands().set(bytes(accessBlockKey(access)), bytes("1"),
                        SetCondition.ifAbsent(), expirationAt(access.expiresAt()));
            }
            connection.keyCommands().del(key);
        });
        return Long.valueOf(1).equals(result);
    }

    private @Nullable Object changeRefresh(UUID userId, Predicate<String> matches,
            BiConsumer<RedisConnection, byte[]> update) {
        byte[] key = bytes(refreshKey(userId));
        RedisCallback<Object> transaction = connection -> {
            connection.watch(key);
            boolean executed = false;
            try {
                byte[] current = connection.stringCommands().get(key);
                if (current == null || !matches.test(new String(current, StandardCharsets.UTF_8))) {
                    return null;
                }

                connection.multi();
                update.accept(connection, key);
                List<Object> results = connection.exec();
                executed = true;
                return results == null || results.isEmpty() ? null : results.getLast();
            } finally {
                if (connection.isQueueing()) {
                    connection.discard();
                } else if (!executed) {
                    connection.unwatch();
                }
            }
        };
        return redis.execute(transaction);
    }

    private static String refreshValue(String hash, Instant expiresAt) {
        return hash + ":" + expiresAt.toEpochMilli();
    }

    private static Expiration expirationAt(Instant expiresAt) {
        return Expiration.unixTimestamp(expiresAt.toEpochMilli(), TimeUnit.MILLISECONDS);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String refreshKey(UUID userId) {
        return "auth:refresh:{" + userId + "}";
    }

    private static String accessBlockKey(VerifiedToken access) {
        return "auth:access:block:{" + access.userId() + "}:" + access.tokenId();
    }

    private static String hash(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes(token));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
