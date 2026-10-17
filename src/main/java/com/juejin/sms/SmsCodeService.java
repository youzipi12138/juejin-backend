package com.juejin.sms;

import com.juejin.common.BusinessException;
import com.juejin.common.PhoneUtil;
import com.juejin.model.vo.SmsCodeVO;
import com.juejin.security.PasswordUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 短信验证码：生成、发送、限流、校验、一次性消费
 */
@Slf4j
@Service
public class SmsCodeService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String KEY_PREFIX = "juejin:sms:";

    private final SmsProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final SmsSender smsSender;
    private final DefaultRedisScript<Long> sendLimitScript;
    private final DefaultRedisScript<Long> verifyScript;

    public SmsCodeService(SmsProperties properties,
                          StringRedisTemplate redisTemplate,
                          SmsSender smsSender) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.smsSender = smsSender;
        this.sendLimitScript = loadScript("lua/sms_send_limit.lua");
        this.verifyScript = loadScript("lua/sms_verify.lua");
        if (properties.isMock()) {
            log.warn("短信服务处于 mock 模式，验证码会输出到日志{}，请勿在生产环境开启",
                    properties.isExposeCode() ? "并返回给接口调用方" : "");
        }
    }

    /**
     * 发送验证码。
     * 限流顺序：60 秒冷却 -> 手机号每日上限 -> IP 每小时上限，均由 Lua 原子执行。
     */
    public SmsCodeVO sendCode(String phone, String clientIp) {
        String normalizedPhone = phone.trim();
        String today = LocalDate.now(ZONE).format(DAY_FORMATTER);
        String thisHour = LocalDateTime.now(ZONE).format(HOUR_FORMATTER);
        String ipPart = clientIp != null && !clientIp.isBlank() ? clientIp : "unknown";

        String cooldownKey = cooldownKey(normalizedPhone);
        String dailyKey = KEY_PREFIX + "daily:" + normalizedPhone + ":" + today;
        String ipKey = KEY_PREFIX + "ip:" + ipPart + ":" + thisHour;

        Long limitResult = redisTemplate.execute(sendLimitScript,
                List.of(cooldownKey, dailyKey, ipKey),
                String.valueOf(properties.getSendInterval().getSeconds()),
                String.valueOf(properties.getDailyLimitPerPhone()),
                String.valueOf(properties.getHourlyLimitPerIp()),
                String.valueOf(Duration.ofDays(1).getSeconds()),
                String.valueOf(Duration.ofHours(1).getSeconds()));

        int status = limitResult != null ? limitResult.intValue() : -99;
        if (status == 0) {
            long remaining = remainingSeconds(cooldownKey, properties.getSendInterval().getSeconds());
            throw new BusinessException(429, "发送过于频繁，请 " + remaining + " 秒后再试");
        }
        if (status == -1) {
            throw new BusinessException(429, "今日验证码发送次数已达上限");
        }
        if (status == -2) {
            throw new BusinessException(429, "当前网络发送验证码过于频繁，请稍后再试");
        }
        if (status != 1) {
            throw new BusinessException(500, "验证码发送失败，请稍后重试");
        }

        String code = generateCode();
        String codeKey = codeKey(normalizedPhone);
        redisTemplate.opsForValue().set(codeKey, hashCode(normalizedPhone, code), properties.getCodeTtl());
        // 重新发送后重置错误次数
        redisTemplate.delete(attemptsKey(normalizedPhone));

        try {
            smsSender.sendCode(normalizedPhone, code);
        } catch (Exception e) {
            // 短信平台调用失败时释放冷却，允许用户立即重试
            redisTemplate.delete(List.of(codeKey, cooldownKey));
            log.error("短信发送失败，phone={}", PhoneUtil.mask(normalizedPhone), e);
            throw new BusinessException(500, "验证码发送失败，请稍后重试");
        }

        boolean exposeCode = properties.isMock() && properties.isExposeCode();
        return SmsCodeVO.builder()
                .phone(PhoneUtil.mask(normalizedPhone))
                .expiresIn(properties.getCodeTtl().getSeconds())
                .retryAfter(properties.getSendInterval().getSeconds())
                .debugCode(exposeCode ? code : null)
                .build();
    }

    /**
     * 校验并消费验证码，成功后验证码立即失效
     */
    public void verifyAndConsume(String phone, String code) {
        String normalizedPhone = phone.trim();
        String hashedInput = hashCode(normalizedPhone, code.trim());

        Long verifyResult = redisTemplate.execute(verifyScript,
                List.of(codeKey(normalizedPhone), attemptsKey(normalizedPhone)),
                hashedInput,
                String.valueOf(properties.getMaxAttempts()),
                String.valueOf(properties.getCodeTtl().getSeconds()));

        int status = verifyResult != null ? verifyResult.intValue() : -2;
        switch (status) {
            case 1 -> {
                // 校验通过
            }
            case 0 -> throw new BusinessException(400, "验证码错误");
            case -1 -> throw new BusinessException(429, "验证码错误次数过多，请重新获取");
            default -> throw new BusinessException(400, "验证码已过期，请重新获取");
        }
    }

    private long remainingSeconds(String key, long defaultValue) {
        Long ttl = redisTemplate.getExpire(key);
        return ttl != null && ttl > 0 ? ttl : defaultValue;
    }

    private String generateCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    /**
     * 验证码不落明文，使用手机号 + 服务端 pepper 作为盐
     */
    private String hashCode(String phone, String code) {
        return PasswordUtil.hash(code, phone + ":" + properties.getPepper());
    }

    private String codeKey(String phone) {
        return KEY_PREFIX + "code:" + phone;
    }

    private String attemptsKey(String phone) {
        return KEY_PREFIX + "attempts:" + phone;
    }

    private String cooldownKey(String phone) {
        return KEY_PREFIX + "cooldown:" + phone;
    }

    private static DefaultRedisScript<Long> loadScript(String classpathLocation) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(classpathLocation)));
        script.setResultType(Long.class);
        return script;
    }
}
