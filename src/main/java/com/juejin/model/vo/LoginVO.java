package com.juejin.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginVO {

    private String token;
    private String tokenType;
    private long expiresIn;
    private UserVO user;

    /**
     * 手机号登录时是否自动注册了新账号
     */
    private boolean newUser;
}
