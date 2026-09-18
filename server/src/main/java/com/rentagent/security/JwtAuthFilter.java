package com.rentagent.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentagent.common.ErrorCode;
import com.rentagent.common.R;
import com.rentagent.entity.SysUser;
import com.rentagent.mapper.SysUserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** JWT 认证过滤器：解析 Bearer Token → 校验用户状态（禁用即时生效，FR-22）→ 写入 UserContext */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final SysUserMapper sysUserMapper;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            long uid;
            int role;
            try {
                Claims claims = jwtUtil.verify(auth.substring(7));
                uid = Long.parseLong(claims.getSubject());
                role = ((Number) claims.get("role")).intValue();
            } catch (Exception e) {
                // 只有"令牌本身不可用"（签名不符 / 已过期 / 字段缺失）才归为 401
                log.debug("Token 校验失败：{}", e.getMessage());
                writeJson(response, 401, R.err(1002, "登录已过期，请重新登录"));
                return;
            }

            SysUser user;
            try {
                user = sysUserMapper.selectById(uid);
            } catch (Exception e) {
                // 数据库等基础设施故障不能伪装成"登录已过期"：那会让前端一律跳登录页、把真实故障掩盖成用户问题
                log.error("加载登录用户失败（uid={}）", uid, e);
                writeJson(response, 200, R.err(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage()));
                return;
            }
            if (user == null || user.getStatus() == null || user.getStatus() == 0) {
                writeJson(response, 401, R.err(ErrorCode.ACCOUNT_DISABLED.getCode(), ErrorCode.ACCOUNT_DISABLED.getMessage()));
                return;
            }
            UserContext.set(new UserContext.User(uid, role));
        }
        try {
            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private void writeJson(HttpServletResponse response, int status, R<Void> body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
