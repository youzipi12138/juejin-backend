package com.juejin.controller;

import com.juejin.common.Result;
import com.juejin.model.dto.LoginRequest;
import com.juejin.model.dto.RegisterRequest;
import com.juejin.model.vo.LoginVO;
import com.juejin.model.vo.UserVO;
import com.juejin.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /**
     * 注册（匿名）
     */
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok("注册成功", userService.register(request));
    }

    /**
     * 登录（匿名），返回 JWT
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok("登录成功", userService.login(request));
    }

    /**
     * 退出登录，删除 Redis 中的 token
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        userService.logout();
        return Result.ok();
    }

    /**
     * 当前登录用户
     */
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(userService.currentUser());
    }
}
