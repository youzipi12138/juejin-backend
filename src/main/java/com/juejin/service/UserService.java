package com.juejin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.juejin.entity.SysUser;
import com.juejin.model.dto.LoginRequest;
import com.juejin.model.dto.RegisterRequest;
import com.juejin.model.dto.SmsLoginRequest;
import com.juejin.model.dto.UserCreateRequest;
import com.juejin.model.dto.UserUpdateRequest;
import com.juejin.model.vo.LoginVO;
import com.juejin.model.vo.PageResult;
import com.juejin.model.vo.UserVO;

public interface UserService extends IService<SysUser> {

    UserVO register(RegisterRequest request);

    LoginVO login(LoginRequest request);

    LoginVO smsLogin(SmsLoginRequest request);

    void logout();

    UserVO currentUser();

    PageResult<UserVO> pageUsers(long current, long size, String username);

    UserVO getUser(Long id);

    UserVO createUser(UserCreateRequest request);

    UserVO updateUser(Long id, UserUpdateRequest request);

    void deleteUser(Long id);
}
