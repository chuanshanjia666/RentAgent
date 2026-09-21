package com.rentagent.service;

import com.rentagent.common.BizException;
import com.rentagent.common.CryptoUtil;
import com.rentagent.dto.AdminDto;
import com.rentagent.dto.AuthDto;
import com.rentagent.dto.PageVO;
import com.rentagent.entity.RealnameAuth;
import com.rentagent.entity.SysUser;
import com.rentagent.mapper.RealnameAuthMapper;
import com.rentagent.mapper.SysUserMapper;
import com.rentagent.security.JwtUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 认证与用户模块单元测试（FR-01~04）：验证码、注册、登录锁定、实名认证。
 * Redis 以 Mock 打桩（TTL/原子性由集成冒烟用例覆盖），BCrypt 与 AES 使用真实实现。
 * 用例编号见 doc/04.编码/单元测试用例设计.md（UT-AUTH-xx）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    private static final String CAPTCHA = "246810";

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private RealnameAuthMapper realnameMapper;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private NotificationService notification;
    @Mock
    private AuditLogService auditLogService;

    private AuthService service;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        CryptoUtil crypto = new CryptoUtil(Base64.getEncoder().encodeToString(new byte[32]));
        service = new AuthService(userMapper, realnameMapper, jwtUtil, redis, crypto, encoder,
                notification, auditLogService);
        ReflectionTestUtils.setField(service, "devCaptcha", CAPTCHA);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(jwtUtil.issue(any(Long.class), any(Integer.class))).thenAnswer(
                inv -> "token-" + inv.getArgument(0));
    }

    private SysUser user(long id, int role, int status) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setUsername("xiaochen");
        u.setPhone("13800000001");
        u.setPassword(encoder.encode("123456"));
        u.setNickname("小陈");
        u.setRole(role);
        u.setStatus(status);
        return u;
    }

    private void assertCode(int expected, Runnable action) {
        BizException e = assertThrows(BizException.class, action::run);
        assertEquals(expected, e.getCode(), "错误码不符，实际 message=" + e.getMessage());
    }

    // ── FR-01 验证码与注册 ──

    @Test
    @DisplayName("UT-AUTH-01 验证码错误或已过期不可注册（1005）")
    void rejectsWrongCaptcha() {
        when(valueOps.get("captcha:13800000009")).thenReturn(CAPTCHA);

        assertCode(1005, () -> service.register(new AuthDto.RegisterReq("13800000009", "000000", "123456", 1, null)));

        when(valueOps.get("captcha:13800000009")).thenReturn(null);
        assertCode(1005, () -> service.register(new AuthDto.RegisterReq("13800000009", CAPTCHA, "123456", 1, null)));

        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("UT-AUTH-02 手机号已注册不可重复注册（1001）")
    void rejectsDuplicatePhone() {
        when(valueOps.get("captcha:13800000001")).thenReturn(CAPTCHA);
        when(userMapper.selectCount(any())).thenReturn(1L);

        assertCode(1001, () -> service.register(new AuthDto.RegisterReq("13800000001", CAPTCHA, "123456", 1, "小陈")));
    }

    @Test
    @DisplayName("UT-AUTH-03 注册成功：用户名手机号一致、默认昵称取后四位、验证码作废并自动登录")
    void registersAndAutoLogins() {
        when(valueOps.get("captcha:13800000009")).thenReturn(CAPTCHA);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.insert(any(SysUser.class))).thenAnswer(inv -> {
            ((SysUser) inv.getArgument(0)).setId(50L);
            return 1;
        });

        AuthDto.TokenResp resp = service.register(
                new AuthDto.RegisterReq("13800000009", CAPTCHA, "123456", 2, null));

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(captor.capture());
        SysUser created = captor.getValue();
        assertEquals("13800000009", created.getUsername());
        assertEquals("用户0009", created.getNickname());
        assertEquals(2, created.getRole());
        assertEquals(1, created.getStatus());
        assertTrue(encoder.matches("123456", created.getPassword()), "密码必须以 BCrypt 存储");
        verify(redis).delete("captcha:13800000009");
        assertEquals(50L, resp.userId());
        assertEquals("token-50", resp.token());
    }

    @Test
    @DisplayName("UT-AUTH-04 注册昵称为空时使用默认昵称，非空时原样落库")
    void fallsBackToDefaultNickname() {
        when(valueOps.get("captcha:13800000009")).thenReturn(CAPTCHA);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.insert(any(SysUser.class))).thenAnswer(inv -> {
            ((SysUser) inv.getArgument(0)).setId(50L);
            return 1;
        });

        service.register(new AuthDto.RegisterReq("13800000009", CAPTCHA, "123456", 1, "  "));

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(captor.capture());
        assertEquals("用户0009", captor.getValue().getNickname());
    }

    // ── FR-02 登录与锁定 ──

    @Test
    @DisplayName("UT-AUTH-05 登录成功清空失败计数并签发 token")
    void resetsFailCountOnLogin() {
        when(valueOps.get("login:fail:xiaochen")).thenReturn("2");
        when(userMapper.selectOne(any())).thenReturn(user(2L, 1, 1));

        AuthDto.TokenResp resp = service.login(new AuthDto.LoginReq("xiaochen", "123456"));

        assertEquals(2L, resp.userId());
        assertEquals("小陈", resp.nickname());
        assertEquals("token-2", resp.token());
        verify(redis).delete("login:fail:xiaochen");
    }

    @Test
    @DisplayName("UT-AUTH-06 密码错误返回 1002，累计失败次数并设 10 分钟过期")
    void countsFailedLogins() {
        when(userMapper.selectOne(any())).thenReturn(user(2L, 1, 1));

        BizException e = assertThrows(BizException.class,
                () -> service.login(new AuthDto.LoginReq("xiaochen", "wrong-password")));

        assertEquals(1002, e.getCode());
        verify(valueOps).increment("login:fail:xiaochen");
        verify(redis).expire(eq("login:fail:xiaochen"), eq(Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("UT-AUTH-07 用户不存在同样返回 1002，不泄漏账号是否存在")
    void hidesWhetherAccountExists() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertCode(1002, () -> service.login(new AuthDto.LoginReq("nobody", "123456")));
    }

    @Test
    @DisplayName("UT-AUTH-08 连续失败 5 次后锁定登录（1003）")
    void locksAfterFiveFailures() {
        when(valueOps.get("login:fail:xiaochen")).thenReturn("5");

        BizException e = assertThrows(BizException.class,
                () -> service.login(new AuthDto.LoginReq("xiaochen", "123456")));
        assertEquals(1003, e.getCode());
        // 锁定期间不再比对密码
        verify(userMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("UT-AUTH-09 已禁用账号不可登录（1004）")
    void rejectsDisabledAccount() {
        when(userMapper.selectOne(any())).thenReturn(user(5L, 1, 0));

        assertCode(1004, () -> service.login(new AuthDto.LoginReq("historyuser", "123456")));
    }

    // ── FR-03 实名认证 ──

    @Test
    @DisplayName("UT-AUTH-10 实名提交：身份证 AES 加密 + SHA-256 双写，初始待审核")
    void storesRealnameEncryptedAndHashed() {
        service.submitRealname(7L, new AuthDto.RealnameReq("李建国", "210102198001011234"));

        ArgumentCaptor<RealnameAuth> captor = ArgumentCaptor.forClass(RealnameAuth.class);
        verify(realnameMapper).insert(captor.capture());
        RealnameAuth auth = captor.getValue();
        assertEquals(7L, auth.getUserId());
        assertEquals(0, auth.getStatus());
        assertNotEquals("210102198001011234", auth.getIdCardNoEnc(), "身份证号不得明文落库");
        assertEquals(64, auth.getIdCardHash().length(), "SHA-256 十六进制长度应为 64");
        assertEquals(new CryptoUtil(Base64.getEncoder().encodeToString(new byte[32]))
                .sha256Hex("210102198001011234"), auth.getIdCardHash());
    }

    @Test
    @DisplayName("UT-AUTH-11 实名通过判定为存在性判断（任一通过记录即放行）")
    void realnamePassedIsExistenceCheck() {
        when(realnameMapper.selectCount(any())).thenReturn(0L);
        assertFalse(service.realnamePassed(7L));

        when(realnameMapper.selectCount(any())).thenReturn(1L);
        assertTrue(service.realnamePassed(7L));
    }

    @Test
    @DisplayName("UT-AUTH-12 实名状态查询：无记录返回 null，有记录时身份证脱敏展示")
    void masksIdCardWhenQuerying() {
        when(realnameMapper.selectList(any())).thenReturn(List.of());
        assertNull(service.realnameOf(7L));

        RealnameAuth auth = new RealnameAuth();
        auth.setId(9L);
        auth.setUserId(7L);
        auth.setRealName("李建国");
        auth.setStatus(1);
        auth.setIdCardNoEnc(new CryptoUtil(Base64.getEncoder().encodeToString(new byte[32]))
                .encrypt("210102198001011234"));
        when(realnameMapper.selectList(any())).thenReturn(List.of(auth));

        AuthDto.RealnameVO vo = service.realnameOf(7L);

        assertEquals("李建国", vo.realName());
        assertEquals(1, vo.status());
        assertEquals("2101**********1234", vo.maskedIdCard());
    }

    @Test
    @DisplayName("UT-AUTH-13 验证码发送写入 5 分钟有效期的演示固定码")
    void sendsDemoCaptcha() {
        service.sendCaptcha("13800000009");

        verify(valueOps).set("captcha:13800000009", CAPTCHA, Duration.ofMinutes(5));
        verify(valueOps, never()).get(anyString());
    }

    @Test
    @DisplayName("UT-AUTH-14 注册角色白名单：role=3（管理员）在服务层同样被拒（1000）")
    void rejectsAdminRoleOnRegister() {
        // DTO 层已用 @Min/@Max 拦截，这里覆盖"绕过控制器直接调服务"的路径
        BizException e = assertThrows(BizException.class, () -> service.register(
                new AuthDto.RegisterReq("13800000009", CAPTCHA, "123456", 3, "小陈")));
        assertEquals(1000, e.getCode());
        verify(userMapper, never()).insert(any(SysUser.class));

        BizException nullRole = assertThrows(BizException.class, () -> service.register(
                new AuthDto.RegisterReq("13800000009", CAPTCHA, "123456", null, "小陈")));
        assertEquals(1000, nullRole.getCode());
    }

    // ── FR-03/07 实名审核 ──

    @Test
    @DisplayName("UT-AUTH-15 实名重复提交：待审核 1009、已通过 1000，均不再插入新记录")
    void rejectsDuplicateRealnameSubmission() {
        RealnameAuth pending = new RealnameAuth();
        pending.setId(9L);
        pending.setUserId(7L);
        pending.setStatus(0);
        when(realnameMapper.selectList(any())).thenReturn(List.of(pending));

        assertCode(1009, () -> service.submitRealname(7L, new AuthDto.RealnameReq("李建国", "210102198001011234")));

        pending.setStatus(1);
        assertCode(1000, () -> service.submitRealname(7L, new AuthDto.RealnameReq("李建国", "210102198001011234")));
        verify(realnameMapper, never()).insert(any(RealnameAuth.class));
    }

    @Test
    @DisplayName("UT-AUTH-16 实名被驳回后可重新提交（status=2 不阻止再次提交）")
    void allowsResubmitAfterRejection() {
        RealnameAuth rejected = new RealnameAuth();
        rejected.setId(9L);
        rejected.setUserId(7L);
        rejected.setStatus(2);
        when(realnameMapper.selectList(any())).thenReturn(List.of(rejected));

        service.submitRealname(7L, new AuthDto.RealnameReq("李建国", "210102198001011234"));

        verify(realnameMapper).insert(any(RealnameAuth.class));
    }

    @Test
    @DisplayName("UT-AUTH-17 实名审核通过：置通过、记录审核人与时间、通知申请人并留痕")
    void approvesRealname() {
        RealnameAuth auth = new RealnameAuth();
        auth.setId(9L);
        auth.setUserId(7L);
        auth.setStatus(0);
        auth.setIdCardHash("a".repeat(64));
        when(realnameMapper.selectById(9L)).thenReturn(auth);
        when(realnameMapper.selectCount(any())).thenReturn(0L);

        service.auditRealname(9L, true, null, 1L);

        ArgumentCaptor<RealnameAuth> captor = ArgumentCaptor.forClass(RealnameAuth.class);
        verify(realnameMapper).updateById(captor.capture());
        RealnameAuth updated = captor.getValue();
        assertEquals(1, updated.getStatus());
        assertNull(updated.getRejectReason());
        assertEquals(1L, updated.getAuditBy());
        assertNotNull(updated.getAuditTime());
        verify(notification).send(eq(7L), eq(2), eq("实名认证已通过"), anyString(), eq("realname"), eq(9L));
        verify(auditLogService).log(eq(1L), eq("REALNAME_PASS"), eq("realname"), eq(9L), any(), eq(null));
    }

    @Test
    @DisplayName("UT-AUTH-18 实名审核驳回：置驳回并保留理由，通知与留痕同步")
    void rejectsRealname() {
        RealnameAuth auth = new RealnameAuth();
        auth.setId(9L);
        auth.setUserId(7L);
        auth.setStatus(0);
        when(realnameMapper.selectById(9L)).thenReturn(auth);

        service.auditRealname(9L, false, "证件照片不清晰", 1L);

        assertEquals(2, auth.getStatus());
        assertEquals("证件照片不清晰", auth.getRejectReason());
        verify(notification).send(eq(7L), eq(2), eq("实名认证未通过"), anyString(), eq("realname"), eq(9L));
        verify(auditLogService).log(eq(1L), eq("REALNAME_REJECT"), eq("realname"), eq(9L), any(), eq(null));
    }

    @Test
    @DisplayName("UT-AUTH-19 已处理过的实名记录不可重复审核（1000），记录不存在返回 1006")
    void rejectsRepeatRealnameAudit() {
        when(realnameMapper.selectById(404L)).thenReturn(null);
        assertCode(1006, () -> service.auditRealname(404L, true, null, 1L));

        RealnameAuth done = new RealnameAuth();
        done.setId(9L);
        done.setStatus(1);
        when(realnameMapper.selectById(9L)).thenReturn(done);
        assertCode(1000, () -> service.auditRealname(9L, true, null, 1L));
        verify(realnameMapper, never()).updateById(any(RealnameAuth.class));
    }

    @Test
    @DisplayName("UT-AUTH-20 同一身份证号已被其他账号认证通过时拒绝再通过（1000）")
    void rejectsDuplicateIdCardOnApproval() {
        RealnameAuth auth = new RealnameAuth();
        auth.setId(9L);
        auth.setUserId(7L);
        auth.setStatus(0);
        auth.setIdCardHash("a".repeat(64));
        when(realnameMapper.selectById(9L)).thenReturn(auth);
        when(realnameMapper.selectCount(any())).thenReturn(1L);

        BizException e = assertThrows(BizException.class, () -> service.auditRealname(9L, true, null, 1L));

        assertEquals(1000, e.getCode());
        assertTrue(e.getMessage().contains("已被其他账号认证通过"));
        verify(realnameMapper, never()).updateById(any(RealnameAuth.class));
    }

    @Test
    @DisplayName("UT-AUTH-21 审核列表：身份证只回脱敏串、带上申请人昵称与手机号")
    void listsRealnameQueueWithMaskedIdCard() {
        RealnameAuth auth = new RealnameAuth();
        auth.setId(9L);
        auth.setUserId(7L);
        auth.setRealName("李建国");
        auth.setStatus(0);
        auth.setIdCardNoEnc(new CryptoUtil(Base64.getEncoder().encodeToString(new byte[32]))
                .encrypt("210102198001011234"));
        Page<RealnameAuth> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(auth));
        when(realnameMapper.selectPage(any(), any())).thenReturn(page);
        SysUser applicant = user(7L, 2, 1);
        applicant.setNickname("王房东");
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(applicant));

        PageVO<AdminDto.RealnameRow> result = service.realnames(0, 1, 10);

        assertEquals(1, result.total());
        AdminDto.RealnameRow row = result.list().get(0);
        assertEquals("王房东", row.nickname());
        assertEquals("2101**********1234", row.maskedIdCard());
        assertFalse(row.maskedIdCard().contains("210102198001011234"), "接口不得回吐明文身份证");
    }
}
