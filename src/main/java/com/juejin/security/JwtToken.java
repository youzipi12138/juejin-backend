package com.juejin.security;

import lombok.RequiredArgsConstructor;
import org.apache.shiro.authc.AuthenticationToken;

/**
 * 交给 Shiro Realm 校验的 JWT 凭证
 */
@RequiredArgsConstructor
public class JwtToken implements AuthenticationToken {

    private final String token;

    @Override
    public Object getPrincipal() {
        return token;
    }

    @Override
    public Object getCredentials() {
        return token;
    }
}
