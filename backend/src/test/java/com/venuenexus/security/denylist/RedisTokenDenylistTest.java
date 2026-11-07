package com.venuenexus.security.denylist;

import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisTokenDenylistTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisTokenDenylist denylist;

    @BeforeEach
    void setUp() {
        denylist = new RedisTokenDenylist(redisTemplate);
    }

    @Test
    void testDenylist_WritesKeyWithTtl_T45() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        Duration ttl = Duration.ofMinutes(10);
        denylist.denylist("test-jti-1", ttl);

        verify(valueOperations).set(eq("auth:denylist:jti:test-jti-1"), eq("1"), eq(ttl));
    }

    @Test
    void testIsDenylisted_PresentAndAbsent() {
        when(redisTemplate.hasKey("auth:denylist:jti:revoked-jti")).thenReturn(true);
        when(redisTemplate.hasKey("auth:denylist:jti:active-jti")).thenReturn(false);

        assertThat(denylist.isDenylisted("revoked-jti")).isTrue();
        assertThat(denylist.isDenylisted("active-jti")).isFalse();
        assertThat(denylist.isDenylisted(null)).isFalse();
    }

    @Test
    void testIsDenylisted_RedisDown_FailsClosed_T46() {
        when(redisTemplate.hasKey("auth:denylist:jti:any-jti"))
                .thenThrow(new RedisConnectionFailureException("Connection refused"));

        // Decision D-P3-8: Fails closed by throwing ApiException(AUTH_TOKEN_REVOKED)
        assertThatThrownBy(() -> denylist.isDenylisted("any-jti"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.AUTH_TOKEN_REVOKED));
    }
}

