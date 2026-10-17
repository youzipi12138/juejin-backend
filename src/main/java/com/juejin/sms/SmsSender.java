package com.juejin.sms;

/**
 * 短信发送器。接入真实平台时新增实现类（如阿里云/腾讯云），
 * 并将 app.sms.mock 设为 false 即可，业务代码无需修改。
 */
public interface SmsSender {

    void sendCode(String phone, String code);
}
