package com.rentagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class PasswordConfig {

    /**
     * 密码编码器无状态、可共享，全应用统一一个实例。
     * <p>
     * 早先 AuthService / AuthController / DataInitializer 各自 {@code new} 一个：
     * 编码强度等参数一旦要调整就会出现"有的地方改了、有的没改"的隐性不一致，
     * 且种子密码与登录校验必须用同一套配置。
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
