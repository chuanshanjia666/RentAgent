package com.rentagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rentagent.common.BizException;
import com.rentagent.common.CryptoUtil;
import com.rentagent.common.ErrorCode;
import com.rentagent.dto.AuthDto;
import com.rentagent.entity.RealnameAuth;
import com.rentagent.entity.SysUser;
import com.rentagent.mapper.RealnameAuthMapper;
import com.rentagent.mapper.SysUserMapper;
import com.rentagent.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String CAPTCHA_KEY = "captcha:";
    private static final String LOGIN_FAIL_KEY = "login:fail:";

    private final SysUserMapper userMapper;
    private final RealnameAuthMapper realnameMapper;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redis;
    private final CryptoUtil crypto;
    private final BCryptPasswordEncoder encoder;

    @Value("${rentagent.captcha-dev-code}")
    private String devCaptcha;

    /** FR-01：发送验证码（演示期固定码；生产切换短信服务商时改造此处） */
    public void sendCaptcha(String phone) {
        redis.opsForValue().set(CAPTCHA_KEY + phone, devCaptcha, Duration.ofMinutes(5));
    }

    /** FR-01：注册即选角色，成功自动登录 */
    @Transactional
    public AuthDto.TokenResp register(AuthDto.RegisterReq req) {
        String captcha = redis.opsForValue().get(CAPTCHA_KEY + req.phone());
        if (captcha == null || !captcha.equals(req.captcha())) {
            throw new BizException(ErrorCode.CAPTCHA_INVALID);
        }
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, req.phone()));
        if (exists > 0) {
            throw new BizException(ErrorCode.ACCOUNT_EXISTS);
        }
        SysUser user = new SysUser();
        user.setUsername(req.phone());
        user.setPhone(req.phone());
        user.setPassword(encoder.encode(req.password()));
        user.setRole(req.role());
        user.setStatus(1);
        user.setNickname(req.nickname() == null || req.nickname().isBlank()
                ? "用户" + req.phone().substring(7) : req.nickname());
        userMapper.insert(user);
        redis.delete(CAPTCHA_KEY + req.phone());
        return tokenOf(user);
    }

    /** FR-02：账号密码登录，错误 5 次锁定 10 分钟 */
    public AuthDto.TokenResp login(AuthDto.LoginReq req) {
        String failKey = LOGIN_FAIL_KEY + req.username();
        String fails = redis.opsForValue().get(failKey);
        if (fails != null && Integer.parseInt(fails) >= 5) {
            throw new BizException(ErrorCode.ACCOUNT_LOCKED);
        }
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, req.username()).or().eq(SysUser::getPhone, req.username())
                .last("LIMIT 1"));
        if (user == null || !encoder.matches(req.password(), user.getPassword())) {
            redis.opsForValue().increment(failKey);
            redis.expire(failKey, Duration.ofMinutes(10));
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }
        if (user.getStatus() == 0) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }
        redis.delete(failKey);
        return tokenOf(user);
    }

    /** FR-03：实名认证提交（AES 加密存储 + 哈希比对字段） */
    @Transactional
    public void submitRealname(long uid, AuthDto.RealnameReq req) {
        RealnameAuth auth = new RealnameAuth();
        auth.setUserId(uid);
        auth.setRealName(req.realName());
        auth.setIdCardNoEnc(crypto.encrypt(req.idCardNo()));
        auth.setIdCardHash(crypto.sha256Hex(req.idCardNo()));
        auth.setStatus(0);
        realnameMapper.insert(auth);
    }

    /** 实名认证状态查询：房东发布房源前的业务校验依据（取最新一条） */
    public AuthDto.RealnameVO realnameOf(long uid) {
        RealnameAuth auth = realnameMapper.selectList(new LambdaQueryWrapper<RealnameAuth>()
                .eq(RealnameAuth::getUserId, uid).orderByDesc(RealnameAuth::getId)
                .last("LIMIT 1")).stream().findFirst().orElse(null);
        if (auth == null) {
            return null;
        }
        String masked = auth.getIdCardNoEnc() == null ? "" : mask(auth.getIdCardNoEnc());
        return new AuthDto.RealnameVO(auth.getRealName(), auth.getStatus(), auth.getRejectReason(), masked);
    }

    /** 房东是否已实名通过（FR-03 验收：未认证房东无法进入房源发布） */
    public boolean realnamePassed(long uid) {
        return realnameMapper.selectCount(new LambdaQueryWrapper<RealnameAuth>()
                .eq(RealnameAuth::getUserId, uid).eq(RealnameAuth::getStatus, 1)) > 0;
    }

    private String mask(String enc) {
        try {
            String plain = crypto.decrypt(enc);
            return plain.length() >= 10 ? plain.substring(0, 4) + "**********" + plain.substring(plain.length() - 4) : "****";
        } catch (Exception e) {
            return "****";
        }
    }

    private AuthDto.TokenResp tokenOf(SysUser user) {
        return new AuthDto.TokenResp(jwtUtil.issue(user.getId(), user.getRole()),
                user.getId(), user.getRole(), user.getNickname());
    }
}
