package com.juejin.sms;

import com.juejin.common.PhoneUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 模拟短信发送器：只打印日志，不调用第三方平台。
 * mock=false 时必须提供真实的 SmsSender 实现，否则应用启动失败（这是有意的保护）。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.sms", name = "mock", havingValue = "true", matchIfMissing = true)
public class MockSmsSender implements SmsSender {

    @Override
    public void sendCode(String phone, String code) {
        log.info("【模拟短信】发送至 {}，验证码：{}", PhoneUtil.mask(phone), code);
    }
}
