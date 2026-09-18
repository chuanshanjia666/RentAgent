package com.rentagent.controller;

import com.rentagent.common.BizException;
import com.rentagent.common.ErrorCode;
import com.rentagent.common.R;
import com.rentagent.dto.AdminDto;
import com.rentagent.dto.AuthDto;
import com.rentagent.dto.AuthDto.RealnameVO;
import com.rentagent.dto.AuthDto.TokenResp;
import com.rentagent.entity.SysUser;
import com.rentagent.mapper.SysUserMapper;
import com.rentagent.security.UserContext;
import com.rentagent.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/** 认证与个人信息（FR-01~04） */
@Tag(name = "auth", description = "认证与个人信息")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SysUserMapper userMapper;
    private final BCryptPasswordEncoder encoder;

    @Operation(summary = "发送验证码（演示期固定 246810）")
    @PostMapping("/auth/captcha")
    public R<Void> captcha(@Valid @RequestBody AuthDto.CaptchaReq req) {
        authService.sendCaptcha(req.phone());
        return R.ok();
    }

    @Operation(summary = "注册（注册即选角色，成功自动登录）")
    @PostMapping("/auth/register")
    public R<TokenResp> register(@Valid @RequestBody AuthDto.RegisterReq req) {
        return R.ok(authService.register(req));
    }

    @Operation(summary = "登录（错误 5 次锁定 10 分钟）")
    @PostMapping("/auth/login")
    public R<TokenResp> login(@Valid @RequestBody AuthDto.LoginReq req) {
        return R.ok(authService.login(req));
    }

    @Operation(summary = "当前用户信息")
    @GetMapping("/users/me")
    public R<AuthDto.MeVO> me() {
        SysUser user = userMapper.selectById(UserContext.userId());
        return R.ok(new AuthDto.MeVO(AdminDto.UserVO.of(user), authService.realnameOf(user.getId())));
    }

    @Operation(summary = "修改个人信息")
    @PatchMapping("/users/me")
    public R<Void> updateProfile(@RequestBody AuthDto.UpdateProfileReq req) {
        SysUser user = userMapper.selectById(UserContext.userId());
        if (req.nickname() != null) {
            user.setNickname(req.nickname());
        }
        if (req.email() != null) {
            user.setEmail(req.email());
        }
        if (req.avatarUrl() != null) {
            user.setAvatarUrl(req.avatarUrl());
        }
        userMapper.updateById(user);
        return R.ok();
    }

    @Operation(summary = "修改密码（成功后前端清除登录态重新登录）")
    @PatchMapping("/users/me/password")
    public R<Void> changePassword(@Valid @RequestBody AuthDto.ChangePasswordReq req) {
        SysUser user = userMapper.selectById(UserContext.userId());
        if (!encoder.matches(req.oldPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.WRONG_PASSWORD);
        }
        user.setPassword(encoder.encode(req.newPassword()));
        userMapper.updateById(user);
        return R.ok();
    }

    @Operation(summary = "提交实名认证（房东发布房源前置条件）")
    @PostMapping("/users/me/realname")
    public R<Void> submitRealname(@Valid @RequestBody AuthDto.RealnameReq req) {
        authService.submitRealname(UserContext.userId(), req);
        return R.ok();
    }

    @Operation(summary = "查询实名认证状态")
    @GetMapping("/users/me/realname")
    public R<RealnameVO> realname() {
        return R.ok(authService.realnameOf(UserContext.userId()));
    }
}
