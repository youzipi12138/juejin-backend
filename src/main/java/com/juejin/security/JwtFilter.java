package com.juejin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.juejin.common.Result;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.web.filter.authc.AuthenticatingFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * JWT 认证过滤器：从请求头取出 token，交给 Shiro Realm 完成认证
 */
@Slf4j
@RequiredArgsConstructor
public class JwtFilter extends AuthenticatingFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String TOKEN_HEADER = "X-Token";

    private final ObjectMapper objectMapper;

    @Override
    protected AuthenticationToken createToken(ServletRequest request, ServletResponse response) {
        String token = resolveToken((HttpServletRequest) request);
        return StringUtils.hasText(token) ? new JwtToken(token) : null;
    }

    @Override
    protected boolean isAccessAllowed(ServletRequest request, ServletResponse response, Object mappedValue) {
        // 放行 CORS 预检请求
        if ("OPTIONS".equalsIgnoreCase(((HttpServletRequest) request).getMethod())) {
            return true;
        }
        return super.isAccessAllowed(request, response, mappedValue);
    }

    @Override
    protected boolean onAccessDenied(ServletRequest request, ServletResponse response) throws Exception {
        if (!StringUtils.hasText(resolveToken((HttpServletRequest) request))) {
            writeUnauthorized(response, "未登录或缺少 token");
            return false;
        }
        return executeLogin(request, response);
    }

    @Override
    protected boolean onLoginFailure(AuthenticationToken token, AuthenticationException e,
                                     ServletRequest request, ServletResponse response) {
        String message = StringUtils.hasText(e.getMessage()) ? e.getMessage() : "认证失败";
        writeUnauthorized(response, message);
        return false;
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization)) {
            return authorization.startsWith(BEARER_PREFIX)
                    ? authorization.substring(BEARER_PREFIX.length()).trim()
                    : authorization.trim();
        }
        String token = request.getHeader(TOKEN_HEADER);
        return StringUtils.hasText(token) ? token.trim() : null;
    }

    private void writeUnauthorized(ServletResponse response, String message) {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.setStatus(HttpStatus.UNAUTHORIZED.value());
        httpResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
        httpResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
        try {
            httpResponse.getWriter().write(objectMapper.writeValueAsString(
                    Result.fail(HttpStatus.UNAUTHORIZED.value(), message)));
        } catch (IOException e) {
            log.warn("写出 401 响应失败: {}", e.getMessage());
        }
    }
}
