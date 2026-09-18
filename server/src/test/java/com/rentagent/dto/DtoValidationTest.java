package com.rentagent.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 请求参数校验单元测试（NFR-03）：Bean Validation 约束防线。
 * 这些约束由 GlobalExceptionHandler 统一映射为 code=1000，是接口测试 UT-API-04 的上游。
 * 用例编号见 doc/04.编码/单元测试用例设计.md（UT-DTO-xx）。
 */
class DtoValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void beforeAll() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void afterAll() {
        factory.close();
    }

    private <T> Set<ConstraintViolation<T>> validate(T target) {
        return validator.validate(target);
    }

    private <T> boolean invalid(T target, String field) {
        return validate(target).stream().anyMatch(v -> v.getPropertyPath().toString().equals(field));
    }

    private AuthDto.RegisterReq register(String phone, String captcha, String password, Integer role) {
        return new AuthDto.RegisterReq(phone, captcha, password, role, "小陈");
    }

    private HouseDto.SaveReq saveReq(BigDecimal area, BigDecimal rent, BigDecimal lng, BigDecimal lat) {
        return new HouseDto.SaveReq("标题", "小区", "大连市", "高新园区", "黄浦路 50 号", "1室1厅",
                area, "南", "中层", rent, "押一付三", List.of(), "描述", lng, lat, List.of());
    }

    // ── FR-01/02 注册登录 ──

    @Test
    @DisplayName("UT-DTO-01 注册：手机号 11 位数字、验证码必填、密码 6~32 位、角色必填")
    void validatesRegisterRequest() {
        assertTrue(validate(register("13800000001", "246810", "123456", 1)).isEmpty());

        assertTrue(invalid(register("1380000", "246810", "123456", 1), "phone"));
        assertTrue(invalid(register("1380000000a", "246810", "123456", 1), "phone"));
        assertTrue(invalid(register("13800000001", " ", "123456", 1), "captcha"));
        assertTrue(invalid(register("13800000001", "246810", "12345", 1), "password"));
        assertTrue(invalid(register("13800000001", "246810", "1".repeat(33), 1), "password"));
        assertTrue(invalid(register("13800000001", "246810", "123456", null), "role"));
    }

    @Test
    @DisplayName("UT-DTO-02 注册角色无取值域约束（现状记录：仅前端限制 1/2）")
    void registerRoleHasNoRangeConstraint() {
        // role=3（管理员）在 DTO 层不被拦截，属于既有实现的宽松点，集成用例不覆盖提权路径
        assertFalse(invalid(register("13800000001", "246810", "123456", 3), "role"));
    }

    @Test
    @DisplayName("UT-DTO-03 登录用户名与密码必填")
    void validatesLoginRequest() {
        assertTrue(validate(new AuthDto.LoginReq("xiaochen", "123456")).isEmpty());
        assertTrue(invalid(new AuthDto.LoginReq("", "123456"), "username"));
        assertTrue(invalid(new AuthDto.LoginReq("xiaochen", ""), "password"));
    }

    @Test
    @DisplayName("UT-DTO-04 修改密码新密码 6~32 位")
    void validatesPasswordChange() {
        assertTrue(validate(new AuthDto.ChangePasswordReq("123456", "abcdef")).isEmpty());
        assertTrue(invalid(new AuthDto.ChangePasswordReq("123456", "abc"), "newPassword"));
        assertTrue(invalid(new AuthDto.ChangePasswordReq("", "abcdef"), "oldPassword"));
    }

    // ── FR-03 实名认证 ──

    @Test
    @DisplayName("UT-DTO-05 实名认证：姓名必填、身份证 18 位（含 X 校验位）")
    void validatesRealnameRequest() {
        assertTrue(validate(new AuthDto.RealnameReq("李建国", "210102198001011234")).isEmpty());
        assertTrue(validate(new AuthDto.RealnameReq("李建国", "21010219800101123X")).isEmpty());

        assertTrue(invalid(new AuthDto.RealnameReq("", "210102198001011234"), "realName"));
        assertTrue(invalid(new AuthDto.RealnameReq("李建国", "21010219800101"), "idCardNo"));
        assertTrue(invalid(new AuthDto.RealnameReq("李建国", "2101021980010112X4"), "idCardNo"));
    }

    // ── FR-05 房源发布 ──

    @Test
    @DisplayName("UT-DTO-06 房源发布：必填项、面积≥1、租金 1~1000000、经纬度合法范围")
    void validatesHousePublishRequest() {
        assertTrue(validate(saveReq(new BigDecimal("45"), new BigDecimal("2100"),
                new BigDecimal("121.54"), new BigDecimal("38.85"))).isEmpty());

        assertTrue(invalid(saveReq(new BigDecimal("0.5"), new BigDecimal("2100"),
                new BigDecimal("121.54"), new BigDecimal("38.85")), "area"));
        assertTrue(invalid(saveReq(new BigDecimal("45"), new BigDecimal("0"),
                new BigDecimal("121.54"), new BigDecimal("38.85")), "rent"));
        assertTrue(invalid(saveReq(new BigDecimal("45"), new BigDecimal("1000001"),
                new BigDecimal("121.54"), new BigDecimal("38.85")), "rent"));
        assertTrue(invalid(saveReq(new BigDecimal("45"), new BigDecimal("2100"),
                new BigDecimal("181"), new BigDecimal("38.85")), "lng"));
        assertTrue(invalid(saveReq(new BigDecimal("45"), new BigDecimal("2100"),
                new BigDecimal("121.54"), new BigDecimal("91")), "lat"));
        assertTrue(invalid(saveReq(new BigDecimal("45"), new BigDecimal("2100"), null, null), "lng"));
        assertTrue(invalid(saveReq(new BigDecimal("45"), new BigDecimal("2100"), null, null), "lat"));
    }

    @Test
    @DisplayName("UT-DTO-07 房源发布必填文本字段不可为空")
    void rejectsBlankHouseTextFields() {
        HouseDto.SaveReq req = new HouseDto.SaveReq("", " ", null, "高新园区", "黄浦路 50 号", "1室1厅",
                new BigDecimal("45"), null, null, new BigDecimal("2100"), "押一付三",
                null, null, new BigDecimal("121.54"), new BigDecimal("38.85"), null);

        Set<ConstraintViolation<HouseDto.SaveReq>> violations = validate(req);

        assertEquals(3, violations.size(), "title/community/city 三个空值必填项应命中");
        assertTrue(invalid(req, "title"));
        assertTrue(invalid(req, "community"));
        assertTrue(invalid(req, "city"));
        assertFalse(invalid(req, "district"), "district 已填写，不应命中");
        assertFalse(invalid(req, "depositType"));
    }

    // ── FR-17~20 交易 ──

    @Test
    @DisplayName("UT-DTO-08 预约创建：房源 id 与到访时间必填")
    void validatesAppointmentCreateRequest() {
        assertTrue(validate(new TradeDto.AppointmentCreateReq(101L,
                LocalDateTime.of(2026, 9, 20, 10, 0), "想看看房")).isEmpty());

        assertTrue(invalid(new TradeDto.AppointmentCreateReq(null, LocalDateTime.now(), null), "houseId"));
        assertTrue(invalid(new TradeDto.AppointmentCreateReq(101L, null, null), "appointmentTime"));
    }

    @Test
    @DisplayName("UT-DTO-09 签约创建：房源 id 与起止日期必填")
    void validatesContractCreateRequest() {
        assertTrue(validate(new TradeDto.ContractCreateReq(101L, LocalDate.now(),
                LocalDate.now().plusMonths(12))).isEmpty());

        assertTrue(invalid(new TradeDto.ContractCreateReq(null, LocalDate.now(), LocalDate.now()), "houseId"));
        assertTrue(invalid(new TradeDto.ContractCreateReq(101L, null, LocalDate.now()), "startDate"));
        assertTrue(invalid(new TradeDto.ContractCreateReq(101L, LocalDate.now(), null), "endDate"));
    }

    @Test
    @DisplayName("UT-DTO-10 评价：订单 id 必填，两项评分均限制 1~5")
    void validatesReviewRequest() {
        assertTrue(validate(new TradeDto.ReviewReq(66L, 1, 5, "很好")).isEmpty());
        assertTrue(validate(new TradeDto.ReviewReq(66L, 5, 1, null)).isEmpty());

        assertTrue(invalid(new TradeDto.ReviewReq(null, 5, 5, null), "leaseOrderId"));
        assertTrue(invalid(new TradeDto.ReviewReq(66L, 0, 5, null), "houseScore"));
        assertTrue(invalid(new TradeDto.ReviewReq(66L, 6, 5, null), "houseScore"));
        assertTrue(invalid(new TradeDto.ReviewReq(66L, 5, 0, null), "landlordScore"));
        assertTrue(invalid(new TradeDto.ReviewReq(66L, 5, 6, null), "landlordScore"));
        assertTrue(invalid(new TradeDto.ReviewReq(66L, null, 5, null), "houseScore"));
    }

    @Test
    @DisplayName("UT-DTO-11 举报：举报类型与目标 id 必填，原因不可为空")
    void validatesReportRequest() {
        assertTrue(validate(new TradeDto.ReportReq(1, 101L, "虚假房源")).isEmpty());

        assertTrue(invalid(new TradeDto.ReportReq(null, 101L, "虚假房源"), "targetType"));
        assertTrue(invalid(new TradeDto.ReportReq(1, null, "虚假房源"), "targetId"));
        assertTrue(invalid(new TradeDto.ReportReq(1, 101L, " "), "reason"));
    }

    // ── FR-12 AI 会话 ──

    @Test
    @DisplayName("UT-DTO-12 AI 会话场景值限制在 1~3")
    void validatesChatSceneRange() {
        assertTrue(validate(new AiDto.SessionCreateReq(1, "找房")).isEmpty());
        assertTrue(validate(new AiDto.SessionCreateReq(3, null)).isEmpty());

        assertTrue(invalid(new AiDto.SessionCreateReq(0, null), "scene"));
        assertTrue(invalid(new AiDto.SessionCreateReq(4, null), "scene"));
        assertTrue(invalid(new AiDto.SessionCreateReq(null, null), "scene"));
    }

    @Test
    @DisplayName("UT-DTO-13 AI 消息内容必填（空串由服务层处理）")
    void validatesChatMessageContent() {
        assertTrue(validate(new AiDto.MessageSendReq("预算 2500 以内")).isEmpty());
        assertTrue(invalid(new AiDto.MessageSendReq(null), "content"));
    }
}
