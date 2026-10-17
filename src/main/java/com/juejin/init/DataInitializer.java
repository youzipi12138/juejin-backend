package com.juejin.init;

import com.juejin.entity.SysUser;
import com.juejin.mapper.SysUserMapper;
import com.juejin.security.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

/**
 * 启动时若不存在 admin 账号则自动创建，方便首次运行
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final SysUserMapper sysUserMapper;

    @Override
    public void run(ApplicationArguments args) {
        long count = sysUserMapper.selectCount(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, "admin"));
        if (count > 0) {
            return;
        }

        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setSalt(PasswordUtil.generateSalt());
        admin.setPassword(PasswordUtil.hash("admin123", admin.getSalt()));
        admin.setNickname("管理员");
        admin.setRole("admin");
        admin.setStatus(1);
        sysUserMapper.insert(admin);

        log.info("已创建初始管理员账号：admin / admin123，请尽快修改密码");
    }
}
