package com.rentagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 认证与用户模块 DTO 集合（FR-01~04） */
public class AuthDto {

    public record CaptchaReq(@NotBlank @Pattern(regexp = "\\d{11}", message = "手机号须为 11 位数字") String phone) {
    }

    public record RegisterReq(
            @NotBlank @Pattern(regexp = "\\d{11}", message = "手机号须为 11 位数字") String phone,
            @NotBlank String captcha,
            @NotBlank @Size(min = 6, max = 32, message = "密码长度 6~32 位") String password,
            @NotNull Integer role, // 1 租客 2 房东
            String nickname) {
    }

    public record LoginReq(@NotBlank String username, // 用户名或手机号
            @NotBlank String password) {
    }

    public record TokenResp(String token, Long userId, Integer role, String nickname) {
    }

    public record UpdateProfileReq(String nickname, String email, String avatarUrl) {
    }

    public record ChangePasswordReq(@NotBlank String oldPassword,
            @NotBlank @Size(min = 6, max = 32, message = "密码长度 6~32 位") String newPassword) {
    }

    public record RealnameReq(@NotBlank String realName,
            @NotBlank @Pattern(regexp = "\\d{17}[\\dXx]", message = "身份证号格式不正确") String idCardNo) {
    }

    public record RealnameVO(String realName, Integer status, String rejectReason, String maskedIdCard) {
    }
}
