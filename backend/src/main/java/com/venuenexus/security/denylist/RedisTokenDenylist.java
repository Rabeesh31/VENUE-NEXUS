package com.venuenexus.security.denylist;

import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.common.filter.TraceIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed implementation of {@link TokenDenylist}.
 *
 * <p><strong>CRITICAL SECURITY INVARIANT:</strong>
 * Implements fail-closed behavior (Decision D-P3-8). If Redis is unreachable during
 * an access token check, it throws {@link ApiException} with {@link ErrorCode#AUTH_TOKEN_REVOKED}
 * rather than allowing potentially revoked tokens through.
 */
@Component
public class RedisTokenDenylist implements TokenDenylist {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenDenylist.class);
    public static final String KEY_PREFIX = "auth:denylist:jti:";

    private final StringRedisTemplate redisTemplate;

    public RedisTokenDenylist(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void denylist(String jti, Duration ttl) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            // Already expired; no need to denylist
            return;
        }

        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
            log.info("Access token denylisted [jti={}, ttl={}]", jti, ttl);
        } catch (Exception ex) {
            log.error("Failed to write to Redis denylist [jti={}, traceId={}]: ", jti, TraceIdFilter.getCurrentTraceId(), ex);
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to record token revocation", ex);
        }
    }

    @Override
    public boolean isDenylisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }

        try {
            Boolean hasKey = redisTemplate.hasKey(KEY_PREFIX + jti);
            return Boolean.TRUE.equals(hasKey);
        } catch (Exception ex) {
            // Fail-closed rule (Decision D-P3-8): If Redis fails, reject token as revoked to guarantee security
            log.error("Redis unreachable during denylist lookup [jti={}, traceId={}]. Failing closed: ",
                    jti, TraceIdFilter.getCurrentTraceId(), ex);
            throw new ApiException(ErrorCode.AUTH_TOKEN_REVOKED, "Security token verification failed due to storage outage", ex);
        }
    }
}
