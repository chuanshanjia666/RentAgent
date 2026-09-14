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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** JWT 认证过滤器：解析 Bearer Token → 校验用户状态（禁用即时生效，FR-22）→ 写入 UserContext */
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
            try {
                Claims claims = jwtUtil.verify(auth.substring(7));
                long uid = Long.parseLong(claims.getSubject());
                int role = ((Number) claims.get("role")).intValue();
                SysUser user = sysUserMapper.selectById(uid);
                if (user == null || user.getStatus() == 0) {
                    writeJson(response, R.err(ErrorCode.ACCOUNT_DISABLED.getCode(), ErrorCode.ACCOUNT_DISABLED.getMessage()));
                    return;
                }
                UserContext.set(new UserContext.User(uid, role));
            } catch (Exception e) {
                writeJson(response, R.err(1002, "登录已过期，请重新登录"));
                return;
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private void writeJson(HttpServletResponse response, R<Void> body) throws IOException {
        response.setStatus(401);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
