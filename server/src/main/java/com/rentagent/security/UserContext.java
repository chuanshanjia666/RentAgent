package com.rentagent.security;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 当前登录用户上下文（ThreadLocal），由 JwtAuthFilter 写入、请求结束清除 */
public class UserContext {

    @Data
    @AllArgsConstructor
    public static class User {
        private Long id;
        private Integer role; // 1租客 2房东 3管理员
    }

    private static final ThreadLocal<User> HOLDER = new ThreadLocal<>();

    public static void set(User user) {
        HOLDER.set(user);
    }

    public static User get() {
        return HOLDER.get();
    }

    public static Long userId() {
        User u = HOLDER.get();
        if (u == null) {
            throw new com.rentagent.common.BizException(com.rentagent.common.ErrorCode.FORBIDDEN);
        }
        return u.getId();
    }

    public static Integer role() {
        User u = HOLDER.get();
        return u == null ? null : u.getRole();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
