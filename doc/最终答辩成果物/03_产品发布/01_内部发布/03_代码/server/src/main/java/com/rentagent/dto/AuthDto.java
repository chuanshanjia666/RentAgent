package com.rentagent.dto;

import com.rentagent.dto.AdminDto.UserVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 认证与用户模块 DTO 集合（FR-01~04） */
public class AuthDto {

    public record CaptchaReq(@NotBlank @Pattern(regexp = "\\d{11}", message = "手机号须为 11 位数字") String phone) {
    }

    /**
     * 注册入参。{@code role} **只能是 1 租客 / 2 房东**：取值域必须在这里就挡死。
     * <p>
     * 早先只标 {@code @NotNull}，服务端又原样采信，导致任何人对公开的
     * {@code /auth/register} 传 {@code role=3} 就能拿到管理员 JWT——角色拦截器完全信任
     * JWT 里的 role，等于免鉴权提权，整个 {@code /admin/**} 失守。
     * 管理员账号不允许自助注册，只能由运维直接落库。
     */
    public record RegisterReq(
            @NotBlank @Pattern(regexp = "\\d{11}", message = "手机号须为 11 位数字") String phone,
            @NotBlank String captcha,
            @NotBlank @Size(min = 6, max = 32, message = "密码长度 6~32 位") String password,
            @NotNull @Min(value = 1, message = "角色只能为 1 租客或 2 房东")
            @Max(value = 2, message = "角色只能为 1 租客或 2 房东") Integer role,
            String nickname) {
    }

    public record LoginReq(@NotBlank String username, // 用户名或手机号
            @NotBlank String password) {
    }

    public record TokenResp(String token, Long userId, Integer role, String nickname) {
    }

    public record UpdateProfileReq(
            @Size(max = 50, message = "昵称最多 50 个字符") String nickname,
            @Size(max = 100, message = "邮箱最多 100 个字符") String email,
            @Size(max = 255, message = "头像地址最多 255 个字符") String avatarUrl) {
    }

    public record ChangePasswordReq(@NotBlank String oldPassword,
            @NotBlank @Size(min = 6, max = 32, message = "密码长度 6~32 位") String newPassword) {
    }

    public record RealnameReq(@NotBlank String realName,
            @NotBlank @Pattern(regexp = "\\d{17}[\\dXx]", message = "身份证号格式不正确") String idCardNo) {
    }

    public record RealnameVO(String realName, Integer status, String rejectReason, String maskedIdCard) {
    }

    /** 当前登录用户信息：user 只含展示字段，realname 未提交过则为 null */
    public record MeVO(UserVO user, RealnameVO realname) {
    }
}
