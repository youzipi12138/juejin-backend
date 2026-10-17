package com.juejin.controller;

import com.juejin.common.Result;
import com.juejin.model.dto.UserCreateRequest;
import com.juejin.model.dto.UserUpdateRequest;
import com.juejin.model.vo.PageResult;
import com.juejin.model.vo.UserVO;
import com.juejin.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理接口，使用 Shiro 注解鉴权
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @RequiresPermissions("user:list")
    public Result<PageResult<UserVO>> page(@RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "10") long size,
                                           @RequestParam(required = false) String username) {
        return Result.ok(userService.pageUsers(current, Math.min(size, 100), username));
    }

    @GetMapping("/{id}")
    @RequiresPermissions("user:read")
    public Result<UserVO> detail(@PathVariable Long id) {
        return Result.ok(userService.getUser(id));
    }

    @PostMapping
    @RequiresPermissions("user:add")
    public Result<UserVO> create(@Valid @RequestBody UserCreateRequest request) {
        return Result.ok("创建成功", userService.createUser(request));
    }

    @PutMapping("/{id}")
    @RequiresPermissions("user:update")
    public Result<UserVO> update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return Result.ok("更新成功", userService.updateUser(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermissions("user:delete")
    public Result<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.ok();
    }
}
