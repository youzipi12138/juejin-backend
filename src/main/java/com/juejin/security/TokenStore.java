package com.juejin.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 基于 Redis 的 token 存储。
 * <p>JWT 签名保证 token 未被篡改，Redis 记录当前有效 token，
 * 用于实现主动退出登录、禁用旧 token（单端登录）。</p>
 */
@Component
@RequiredArgsConstructor
public class TokenStore {

    private static final String KEY_PREFIX = "juejin:login:token:";

    private final StringRedisTemplate stringRedisTemplate;

    public void save(Long userId, String token, Duration ttl) {
        stringRedisTemplate.opsForValue().set(buildKey(userId), token, ttl);
    }

    public String get(Long userId) {
        return stringRedisTemplate.opsForValue().get(buildKey(userId));
    }

    public void remove(Long userId) {
        stringRedisTemplate.delete(buildKey(userId));
    }

    private String buildKey(Long userId) {
        return KEY_PREFIX + userId;
    }
}
