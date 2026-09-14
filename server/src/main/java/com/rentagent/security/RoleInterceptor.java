package com.rentagent.security;

import com.rentagent.common.BizException;
import com.rentagent.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** 角色拦截器：读取 @RequireRole 并校验当前用户角色；未登录请求被公开路径放行，受保护接口在控制器内显式标注 */
@Component
public class RoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RequireRole anno = method.getMethodAnnotation(RequireRole.class);
        if (anno == null) {
            anno = method.getBeanType().getAnnotation(RequireRole.class);
        }
        if (anno == null) {
            return true;
        }
        UserContext.User user = UserContext.get();
        if (user == null) {
            throw new BizException(1002, "请先登录");
        }
        for (int allowed : anno.value()) {
            if (allowed == user.getRole()) {
                return true;
            }
        }
        throw new BizException(ErrorCode.FORBIDDEN);
    }
}
