package com.juejin.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SmsCodeVO {

    /**
     * 脱敏后的手机号
     */
    private String phone;

    /**
     * 验证码有效期（秒）
     */
    private long expiresIn;

    /**
     * 下次可发送的间隔（秒）
     */
    private long retryAfter;

    /**
     * 仅 mock 模式 + expose-code=true 时返回，方便本地联调
     */
    private String debugCode;
}
