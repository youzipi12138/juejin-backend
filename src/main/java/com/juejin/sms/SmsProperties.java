package com.juejin.sms;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "app.sms")
public class SmsProperties {

    /**
     * 是否使用模拟短信发送器（无第三方短信平台时开启）
     */
    private boolean mock = true;

    /**
     * mock 模式下是否在接口响应中返回验证码，生产环境必须关闭
     */
    private boolean exposeCode = true;

    /**
     * 验证码有效期
     */
    private Duration codeTtl = Duration.ofMinutes(5);

    /**
     * 同一手机号发送间隔
     */
    private Duration sendInterval = Duration.ofSeconds(60);

    /**
     * 单个验证码允许的最大错误次数
     */
    private int maxAttempts = 5;

    /**
     * 同一手机号每日最多发送次数
     */
    private int dailyLimitPerPhone = 10;

    /**
     * 同一 IP 每小时最多发送次数
     */
    private int hourlyLimitPerIp = 30;

    /**
     * 验证码哈希用的服务端密钥
     */
    private String pepper = "juejin-sms-pepper-change-me";
}
