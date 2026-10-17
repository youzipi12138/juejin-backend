package com.juejin.security;

import org.apache.shiro.crypto.hash.SimpleHash;

import java.util.UUID;

/**
 * 密码加密工具：SHA-256 + 随机盐 + 1024 次迭代
 */
public final class PasswordUtil {

    private static final String ALGORITHM = "SHA-256";
    private static final int ITERATIONS = 1024;

    private PasswordUtil() {
    }

    public static String generateSalt() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String hash(String rawPassword, String salt) {
        return new SimpleHash(ALGORITHM, rawPassword, salt, ITERATIONS).toHex();
    }

    public static boolean matches(String rawPassword, String salt, String encryptedPassword) {
        return hash(rawPassword, salt).equalsIgnoreCase(encryptedPassword);
    }
}
