package com.juejin.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserUpdateRequest {

    @Size(max = 50, message = "昵称长度不能超过 50")
    private String nickname;

    /**
     * 不传表示不修改密码
     */
    @Size(min = 6, max = 32, message = "密码长度需在 6-32 之间")
    private String password;

    @Pattern(regexp = "admin|user", message = "角色只能是 admin 或 user")
    private String role;

    @Min(value = 0, message = "状态只能是 0 或 1")
    @Max(value = 1, message = "状态只能是 0 或 1")
    private Integer status;
}
