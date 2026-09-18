package com.rentagent.security;

import com.rentagent.common.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * 鉴权链路单元测试（NFR-02/NFR-04）：JWT 签发与篡改、角色拦截器、线程上下文。
 * 用例编号见 doc/04.编码/单元测试用例设计.md（UT-SEC-xx）。
 */
class SecurityGuardTest {

    private static final String SECRET = "rentagent-unit-test-secret-key-0123456789";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 72);
    private final RoleInterceptor interceptor = new RoleInterceptor();

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ── JWT ──

    @Test
    @DisplayName("UT-SEC-01 签发后可校验出用户 id 与角色")
    void issuesAndVerifiesToken() {
        String token = jwtUtil.issue(42L, 2);

        Claims claims = jwtUtil.verify(token);

        assertEquals("42", claims.getSubject());
        assertEquals(2, claims.get("role", Integer.class));
        assertNotNull(claims.getId());
        assertNotNull(claims.getExpiration());
    }

    @Test
    @DisplayName("UT-SEC-02 篡改 token 校验失败")
    void rejectsTamperedToken() {
        String token = jwtUtil.issue(42L, 2);
        String tampered = token.substring(0, token.length() - 3) + "abc";

        assertThrows(JwtException.class, () -> jwtUtil.verify(tampered));
        assertThrows(JwtException.class, () -> jwtUtil.verify(token + "x"));
        assertThrows(JwtException.class, () -> jwtUtil.verify("not-a-jwt"));
    }

    @Test
    @DisplayName("UT-SEC-03 换密钥签发（伪造者）无法通过校验")
    void rejectsTokenSignedWithOtherKey() {
        String forged = new JwtUtil("another-secret-key-for-forging-0123456789", 72).issue(1L, 3);

        assertThrows(JwtException.class, () -> jwtUtil.verify(forged));
    }

    @Test
    @DisplayName("UT-SEC-04 过期 token 校验失败")
    void rejectsExpiredToken() {
        String expired = new JwtUtil(SECRET, -1).issue(42L, 1);

        assertThrows(JwtException.class, () -> jwtUtil.verify(expired));
    }

    // ── 角色拦截器 ──

    private HandlerMethod handler(String methodName) throws NoSuchMethodException {
        Object bean = new OpenController();
        Method method = OpenController.class.getMethod(methodName);
        return new HandlerMethod(bean, method);
    }

    private HandlerMethod adminHandler() throws NoSuchMethodException {
        Method method = AdminOnlyController.class.getMethod("list");
        return new HandlerMethod(new AdminOnlyController(), method);
    }

    @Test
    @DisplayName("UT-SEC-05 无角色注解的接口放行（含匿名）")
    void allowsEndpointWithoutAnnotation() throws Exception {
        assertNull(UserContext.get());
        assertEquals(true, interceptor.preHandle(mock(HttpServletRequest.class), null, handler("open")));
    }

    @Test
    @DisplayName("UT-SEC-06 未登录访问受限接口返回 1002「请先登录」")
    void rejectsAnonymousOnGuardedEndpoint() throws Exception {
        BizException e = assertThrows(BizException.class, () ->
                interceptor.preHandle(mock(HttpServletRequest.class), null, adminHandler()));

        assertEquals(1002, e.getCode());
        assertEquals("请先登录", e.getMessage());
    }

    @Test
    @DisplayName("UT-SEC-07 已登录但角色不符返回 1007")
    void rejectsWrongRole() throws Exception {
        UserContext.set(new UserContext.User(2L, 1));

        BizException e = assertThrows(BizException.class, () ->
                interceptor.preHandle(mock(HttpServletRequest.class), null, adminHandler()));

        assertEquals(1007, e.getCode());
    }

    @Test
    @DisplayName("UT-SEC-08 类级注解对全部方法生效，方法级注解可多角色")
    void supportsMethodAndClassLevelAnnotations() throws Exception {
        UserContext.set(new UserContext.User(1L, 3));
        assertEquals(true, interceptor.preHandle(mock(HttpServletRequest.class), null, adminHandler()));

        UserContext.set(new UserContext.User(7L, 2));
        assertEquals(true, interceptor.preHandle(mock(HttpServletRequest.class), null, handler("upload")));

        UserContext.set(new UserContext.User(2L, 1));
        assertThrows(BizException.class, () ->
                interceptor.preHandle(mock(HttpServletRequest.class), null, handler("upload")));
    }

    @Test
    @DisplayName("UT-SEC-09 非控制器方法（静态资源等）直接放行")
    void allowsNonHandlerMethod() {
        assertEquals(true, interceptor.preHandle(mock(HttpServletRequest.class), null, "resource"));
    }

    // ── 线程上下文 ──

    @Test
    @DisplayName("UT-SEC-10 未登录读取用户 id 返回 1007，role 返回 null")
    void failsReadingContextWhenAnonymous() {
        BizException e = assertThrows(BizException.class, UserContext::userId);
        assertEquals(1007, e.getCode());
        assertNull(UserContext.role());
        assertNull(UserContext.get());
    }

    @Test
    @DisplayName("UT-SEC-11 上下文写入后可读，clear 后不残留（ThreadLocal 防泄漏）")
    void setsAndClearsContext() {
        UserContext.set(new UserContext.User(2L, 1));
        assertEquals(2L, UserContext.userId());
        assertEquals(1, UserContext.role());

        UserContext.clear();
        assertNull(UserContext.get());
        assertThrows(BizException.class, UserContext::userId);
    }

    /** 接口定义替身：用于构造方法级/类级 @RequireRole 场景 */
    static class OpenController {
        public void open() {
        }

        @RequireRole({2, 3})
        public void upload() {
        }
    }

    @RequireRole(3)
    static class AdminOnlyController {
        public void list() {
        }
    }
}
