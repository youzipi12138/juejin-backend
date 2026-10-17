package com.juejin.security;

import com.juejin.entity.SysUser;
import com.juejin.mapper.SysUserMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.ExpiredCredentialsException;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.LockedAccountException;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;

/**
 * 认证：校验 JWT 签名、有效期以及 Redis 中记录的 token
 * 授权：根据角色授予权限（示例为硬编码，生产环境可扩展为角色/权限表）
 */
public class JwtRealm extends AuthorizingRealm {

    private final SysUserMapper sysUserMapper;
    private final JwtUtil jwtUtil;
    private final TokenStore tokenStore;

    public JwtRealm(SysUserMapper sysUserMapper, JwtUtil jwtUtil, TokenStore tokenStore) {
        this.sysUserMapper = sysUserMapper;
        this.jwtUtil = jwtUtil;
        this.tokenStore = tokenStore;
    }

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof JwtToken;
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken authenticationToken)
            throws AuthenticationException {
        String token = (String) authenticationToken.getCredentials();

        Long userId;
        try {
            userId = jwtUtil.getUserId(token);
        } catch (ExpiredJwtException e) {
            throw new ExpiredCredentialsException("登录已过期，请重新登录");
        } catch (JwtException | IllegalArgumentException e) {
            throw new IncorrectCredentialsException("无效的 token");
        }

        String cachedToken = tokenStore.get(userId);
        if (cachedToken == null) {
            throw new ExpiredCredentialsException("登录状态已失效，请重新登录");
        }
        if (!cachedToken.equals(token)) {
            throw new IncorrectCredentialsException("登录状态已失效，请重新登录");
        }

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new UnknownAccountException("用户不存在");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new LockedAccountException("账号已被禁用");
        }

        return new SimpleAuthenticationInfo(user, token, getName());
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        if (!(principals.getPrimaryPrincipal() instanceof SysUser user)) {
            return null;
        }

        SimpleAuthorizationInfo authorizationInfo = new SimpleAuthorizationInfo();
        String role = user.getRole();
        authorizationInfo.addRole(role);
        if ("admin".equals(role)) {
            // 通配权限，拥有所有接口权限
            authorizationInfo.addStringPermission("*");
        } else {
            authorizationInfo.addStringPermission("user:read");
        }
        return authorizationInfo;
    }
}
