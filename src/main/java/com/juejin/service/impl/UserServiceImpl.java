package com.juejin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.juejin.common.BusinessException;
import com.juejin.entity.SysUser;
import com.juejin.mapper.SysUserMapper;
import com.juejin.model.dto.LoginRequest;
import com.juejin.model.dto.RegisterRequest;
import com.juejin.model.dto.UserCreateRequest;
import com.juejin.model.dto.UserUpdateRequest;
import com.juejin.model.vo.LoginVO;
import com.juejin.model.vo.PageResult;
import com.juejin.model.vo.UserVO;
import com.juejin.security.JwtUtil;
import com.juejin.security.PasswordUtil;
import com.juejin.security.TokenStore;
import com.juejin.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements UserService {

    private static final String ROLE_ADMIN = "admin";
    private static final String ROLE_USER = "user";
    private static final int STATUS_ENABLED = 1;

    private final JwtUtil jwtUtil;
    private final TokenStore tokenStore;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO register(RegisterRequest request) {
        String username = request.getUsername().trim();
        ensureUsernameAvailable(username);

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setSalt(PasswordUtil.generateSalt());
        user.setPassword(PasswordUtil.hash(request.getPassword(), user.getSalt()));
        user.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname().trim() : username);
        user.setRole(ROLE_USER);
        user.setStatus(STATUS_ENABLED);
        this.save(user);
        return UserVO.from(user);
    }

    @Override
    public LoginVO login(LoginRequest request) {
        SysUser user = this.lambdaQuery()
                .eq(SysUser::getUsername, request.getUsername().trim())
                .one();

        if (user == null || !PasswordUtil.matches(request.getPassword(), user.getSalt(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (!Integer.valueOf(STATUS_ENABLED).equals(user.getStatus())) {
            throw new BusinessException(403, "账号已被禁用");
        }

        long expireSeconds = jwtUtil.getExpireSeconds();
        String token = jwtUtil.generate(user.getId(), user.getUsername());
        // 覆盖写入，旧 token 立即失效（单端登录）
        tokenStore.save(user.getId(), token, Duration.ofSeconds(expireSeconds));
        return new LoginVO(token, "Bearer", expireSeconds, UserVO.from(user));
    }

    @Override
    public void logout() {
        tokenStore.remove(currentUserEntity().getId());
    }

    @Override
    public UserVO currentUser() {
        return UserVO.from(currentUserEntity());
    }

    @Override
    public PageResult<UserVO> pageUsers(long current, long size, String username) {
        Page<SysUser> page = new Page<>(current, size);
        IPage<SysUser> result = this.page(page, Wrappers.<SysUser>lambdaQuery()
                .like(StringUtils.hasText(username), SysUser::getUsername,
                        StringUtils.hasText(username) ? username.trim() : null)
                .orderByDesc(SysUser::getId));
        return PageResult.of(result.convert(UserVO::from));
    }

    @Override
    public UserVO getUser(Long id) {
        return UserVO.from(getExistingUser(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO createUser(UserCreateRequest request) {
        String username = request.getUsername().trim();
        ensureUsernameAvailable(username);

        String role = StringUtils.hasText(request.getRole()) ? request.getRole() : ROLE_USER;
        validateRole(role);
        int status = request.getStatus() != null ? request.getStatus() : STATUS_ENABLED;

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setSalt(PasswordUtil.generateSalt());
        user.setPassword(PasswordUtil.hash(request.getPassword(), user.getSalt()));
        user.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname().trim() : username);
        user.setRole(role);
        user.setStatus(status);
        this.save(user);
        return UserVO.from(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateUser(Long id, UserUpdateRequest request) {
        SysUser user = getExistingUser(id);

        if (StringUtils.hasText(request.getRole())) {
            validateRole(request.getRole());
            user.setRole(request.getRole());
        }
        if (StringUtils.hasText(request.getNickname())) {
            user.setNickname(request.getNickname().trim());
        }
        if (StringUtils.hasText(request.getPassword())) {
            user.setSalt(PasswordUtil.generateSalt());
            user.setPassword(PasswordUtil.hash(request.getPassword(), user.getSalt()));
            // 修改密码后让旧 token 失效
            tokenStore.remove(id);
        }
        if (request.getStatus() != null) {
            if (request.getStatus() == 0 && id.equals(currentUserEntity().getId())) {
                throw new BusinessException("不能禁用当前登录用户");
            }
            user.setStatus(request.getStatus());
            if (request.getStatus() == 0) {
                tokenStore.remove(id);
            }
        }

        this.updateById(user);
        return UserVO.from(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        if (id.equals(currentUserEntity().getId())) {
            throw new BusinessException("不能删除当前登录用户");
        }
        getExistingUser(id);
        this.removeById(id);
        tokenStore.remove(id);
    }

    private SysUser getExistingUser(Long id) {
        SysUser user = this.getById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return user;
    }

    private void ensureUsernameAvailable(String username) {
        long count = this.lambdaQuery().eq(SysUser::getUsername, username).count();
        if (count > 0) {
            throw new BusinessException("用户名已存在");
        }
    }

    private void validateRole(String role) {
        if (!ROLE_ADMIN.equals(role) && !ROLE_USER.equals(role)) {
            throw new BusinessException("角色只能是 admin 或 user");
        }
    }

    private SysUser currentUserEntity() {
        Object principal = SecurityUtils.getSubject().getPrincipal();
        if (!(principal instanceof SysUser user)) {
            throw new BusinessException(401, "未登录");
        }
        return user;
    }
}
