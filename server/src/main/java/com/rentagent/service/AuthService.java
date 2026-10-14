package com.rentagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rentagent.common.BizException;
import com.rentagent.common.CryptoUtil;
import com.rentagent.common.ErrorCode;
import com.rentagent.dto.AdminDto;
import com.rentagent.dto.AuthDto;
import com.rentagent.dto.PageVO;
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
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final NotificationService notification;
    private final AuditLogService auditLogService;

    @Value("${rentagent.captcha-dev-code}")
    private String devCaptcha;

    /** FR-01：发送验证码（演示期固定码；生产切换短信服务商时改造此处） */
    public void sendCaptcha(String phone) {
        redis.opsForValue().set(CAPTCHA_KEY + phone, devCaptcha, Duration.ofMinutes(5));
    }

    /** FR-01：注册即选角色，成功自动登录 */
    @Transactional
    public AuthDto.TokenResp register(AuthDto.RegisterReq req) {
        // 角色白名单在服务层再兜一次：DTO 校验只覆盖走 @Valid 的控制器入参，
        // 服务被直接调用（测试、内部编排、后续新增入口）时不能没有这道闸
        Integer role = req.role();
        if (role == null || (role != 1 && role != 2)) {
            throw new BizException(ErrorCode.PARAM_INVALID.getCode(), "角色只能为 1 租客或 2 房东");
        }
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
        user.setRole(role);
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

    /**
     * FR-03：实名认证提交（AES 加密存储 + 哈希比对字段）。
     * <p>
     * 待审核与已通过都不允许重复提交：重复提交会堆出多条记录，而 {@code realnamePassed}
     * 与后台审核列表都按记录维度处理，重复记录会让"同一证件认证几个账号"变得不可判断。
     * 被驳回（status=2）后允许重新提交，这也是房东改错证件号后的唯一补救路径。
     */
    @Transactional
    public void submitRealname(long uid, AuthDto.RealnameReq req) {
        RealnameAuth last = latestOf(uid);
        if (last != null && last.getStatus() == 0) {
            throw new BizException(ErrorCode.REALNAME_PENDING);
        }
        if (last != null && last.getStatus() == 1) {
            throw new BizException(ErrorCode.PARAM_INVALID.getCode(), "已完成实名认证，无需重复提交");
        }
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
        RealnameAuth auth = latestOf(uid);
        if (auth == null) {
            return null;
        }
        String masked = auth.getIdCardNoEnc() == null ? "" : mask(auth.getIdCardNoEnc());
        return new AuthDto.RealnameVO(auth.getRealName(), auth.getStatus(), auth.getRejectReason(), masked);
    }

    /**
     * FR-03/07：实名认证审核队列（{@code status} -1 全部 / 0 待审核 / 1 通过 / 2 驳回）。
     * 身份证号只回脱敏串：明文以 AES 密文落库（NFR-04），密文本身对前端也没有意义。
     */
    public PageVO<AdminDto.RealnameRow> realnames(int status, long page, long size) {
        Page<RealnameAuth> p = realnameMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<RealnameAuth>()
                        .eq(status >= 0, RealnameAuth::getStatus, status)
                        .orderByAsc(RealnameAuth::getStatus).orderByDesc(RealnameAuth::getId));
        Map<Long, SysUser> users = p.getRecords().isEmpty() ? Map.of()
                : userMapper.selectBatchIds(p.getRecords().stream().map(RealnameAuth::getUserId).distinct().toList())
                        .stream().collect(Collectors.toMap(SysUser::getId, u -> u));
        return PageVO.map(p, a -> {
            SysUser u = users.get(a.getUserId());
            return new AdminDto.RealnameRow(a.getId(), a.getUserId(),
                    u == null ? null : u.getNickname(), u == null ? null : u.getPhone(),
                    a.getRealName(), a.getIdCardNoEnc() == null ? "" : mask(a.getIdCardNoEnc()),
                    a.getStatus(), a.getRejectReason(), a.getAuditTime(), a.getCreatedAt());
        });
    }

    /**
     * FR-03/07：实名认证审核（通过 / 驳回），通知与留痕都在服务层完成。
     * <p>
     * 通过时校验身份证哈希唯一：同一证件不得被两个账号同时认证通过（一人多号刷账号）。
     * 留痕写 {@code audit_log}，与房源审核、举报处理同一口径。
     */
    @Transactional
    public void auditRealname(long id, boolean pass, String reason, long adminId) {
        RealnameAuth auth = realnameMapper.selectById(id);
        if (auth == null) {
            throw new BizException(ErrorCode.REALNAME_NOT_FOUND);
        }
        if (auth.getStatus() == null || auth.getStatus() != 0) {
            throw new BizException(ErrorCode.PARAM_INVALID.getCode(), "该实名认证已处理，请勿重复审核");
        }
        if (!pass && (reason == null || reason.isBlank())) {
            throw new BizException(ErrorCode.PARAM_INVALID.getCode(), "驳回必须填写理由");
        }
        if (pass) {
            Long dup = realnameMapper.selectCount(new LambdaQueryWrapper<RealnameAuth>()
                    .eq(RealnameAuth::getIdCardHash, auth.getIdCardHash())
                    .eq(RealnameAuth::getStatus, 1)
                    .ne(RealnameAuth::getId, id));
            if (dup > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID.getCode(), "该身份证号已被其他账号认证通过");
            }
        }
        auth.setStatus(pass ? 1 : 2);
        auth.setRejectReason(pass ? null : reason.trim());
        auth.setAuditBy(adminId);
        auth.setAuditTime(LocalDateTime.now());
        realnameMapper.updateById(auth);
        notification.send(auth.getUserId(), 2, pass ? "实名认证已通过" : "实名认证未通过",
                pass ? "实名认证已通过，现在可以发布房源了"
                        : "实名认证未通过：" + (reason == null || reason.isBlank() ? "信息有误，请重新提交" : reason),
                "realname", auth.getId());
        auditLogService.log(adminId, pass ? "REALNAME_PASS" : "REALNAME_REJECT", "realname", id,
                Map.of("pass", pass, "reason", reason == null ? "" : reason), null);
    }

    /** 房东是否已实名通过（FR-03 验收：未认证房东无法进入房源发布） */
    public boolean realnamePassed(long uid) {
        return realnameMapper.selectCount(new LambdaQueryWrapper<RealnameAuth>()
                .eq(RealnameAuth::getUserId, uid).eq(RealnameAuth::getStatus, 1)) > 0;
    }

    /** 最近一次实名认证记录（提交去重与状态查询共用同一口径） */
    private RealnameAuth latestOf(long uid) {
        return realnameMapper.selectList(new LambdaQueryWrapper<RealnameAuth>()
                .eq(RealnameAuth::getUserId, uid).orderByDesc(RealnameAuth::getId)
                .last("LIMIT 1")).stream().findFirst().orElse(null);
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

    /** 当前登录用户信息与实名状态 */
    public AuthDto.MeVO currentUser(long uid) {
        SysUser user = userMapper.selectById(uid);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        return new AuthDto.MeVO(AdminDto.UserVO.of(user), realnameOf(user.getId()));
    }

    /** 修改个人信息 */
    @Transactional
    public void updateProfile(long uid, AuthDto.UpdateProfileReq req) {
        SysUser user = userMapper.selectById(uid);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        if (req.nickname() != null) {
            user.setNickname(req.nickname().isBlank() ? null : req.nickname().trim());
        }
        if (req.email() != null) {
            user.setEmail(req.email().isBlank() ? null : req.email().trim());
        }
        if (req.avatarUrl() != null) {
            user.setAvatarUrl(req.avatarUrl().isBlank() ? null : req.avatarUrl().trim());
        }
        userMapper.updateById(user);
    }

    /** 修改密码：校验旧密码并 BCrypt 加密保存新密码 */
    @Transactional
    public void changePassword(long uid, AuthDto.ChangePasswordReq req) {
        SysUser user = userMapper.selectById(uid);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        if (!encoder.matches(req.oldPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.WRONG_PASSWORD);
        }
        user.setPassword(encoder.encode(req.newPassword()));
        userMapper.updateById(user);
    }
}
