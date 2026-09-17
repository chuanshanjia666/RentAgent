package com.rentagent.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统一响应体与全局异常处理单元测试（NFR-03）：业务码透传、参数校验错误码、异常不外泄堆栈。
 * 用例编号见 doc/04.编码/单元测试用例设计.md（UT-API-xx）。
 */
class ApiContractTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("UT-API-01 成功响应固定 code=0 / message=ok")
    void 成功响应结构() {
        R<String> ok = R.ok("data");

        assertEquals(0, ok.getCode());
        assertEquals("ok", ok.getMessage());
        assertEquals("data", ok.getData());

        R<Void> empty = R.ok();
        assertEquals(0, empty.getCode());
        assertNull(empty.getData());
    }

    @Test
    @DisplayName("UT-API-02 业务异常按原错误码与文案透传，data 为空")
    void 业务异常透传() {
        R<Void> r = handler.handleBiz(new BizException(ErrorCode.APPOINTMENT_CONFLICT));

        assertEquals(3001, r.getCode());
        assertEquals("该时段已被预约，请换个时间", r.getMessage());
        assertNull(r.getData());
    }

    @Test
    @DisplayName("UT-API-03 自定义文案业务码（1000/3003 等复用码）按 message 区分语义")
    void 自定义文案业务码() {
        R<Void> r = handler.handleBiz(BizException.of(ErrorCode.REVIEW_NOT_ALLOWED, "该订单已评价过"));

        assertEquals(3005, r.getCode());
        assertEquals("该订单已评价过", r.getMessage());
    }

    @Test
    @DisplayName("UT-API-04 参数校验失败统一返回 1000 且附带字段名")
    void 参数校验失败返回1000() throws Exception {
        Method method = ApiContractTest.class.getDeclaredMethod("endpoint", String.class);
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "registerReq");
        binding.addError(new FieldError("registerReq", "phone", "手机号须为 11 位数字"));
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(new MethodParameter(method, 0), binding);

        R<Void> r = handler.handleValid(ex);

        assertEquals(1000, r.getCode());
        assertTrue(r.getMessage().contains("phone"));
        assertTrue(r.getMessage().contains("手机号须为 11 位数字"));
    }

    @Test
    @DisplayName("UT-API-05 未预期异常统一收敛为 5000，不外泄内部信息")
    void 未预期异常收敛() {
        R<Void> r = handler.handleOther(new IllegalStateException("数据库连接串 jdbc:mysql://secret"));

        assertEquals(5000, r.getCode());
        assertEquals("系统繁忙，请稍后再试", r.getMessage());
        assertNull(r.getData());
        assertNotNull(r.getMessage());
    }

    @SuppressWarnings("unused")
    private void endpoint(String body) {
    }
}
