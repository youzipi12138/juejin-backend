package com.juejin.common;

/**
 * 手机号工具
 */
public final class PhoneUtil {

    private PhoneUtil() {
    }

    /**
     * 13812345678 -> 138****5678
     */
    public static String mask(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
