package com.rentagent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 启动自检（NFR-04）：检测"仍是仓库内置默认值"的凭据并显式告警。
 * <p>
 * 为什么只告警不拒绝启动：本课程项目的演示流程（本机 {@code run-dev.sh}、CI 集成作业）
 * 都不注入这些环境变量，fail-fast 会把演示与门禁一起打死。但默认 JWT 密钥意味着
 * 任何人可以离线伪造 uid 任意、role=3 的 token 绕过整条鉴权，默认 AES 密钥意味着
 * 库里的身份证密文等同明文——这类实例一旦对外可达就是事故，所以必须在启动日志里
 * 留下明确、可被搜索到的痕迹。
 */
@Slf4j
@Component
public class StartupConfigCheck implements ApplicationRunner {

    /** application.yml 中 jwt-secret 的默认值（出现即说明未用环境变量覆盖） */
    static final String DEFAULT_JWT_SECRET =
            "rentagent-dev-secret-key-please-change-in-production-0123456789abcdef";
    /** application.yml 中 aes-key 的默认值 */
    static final String DEFAULT_AES_KEY = "cmVudGFnZW50LWRldi1rZXktMzItYnl0ZXMtYWJjZCE=";
    /** application.yml 中 datasource.password 的默认值 */
    static final String DEFAULT_DB_PASSWORD = "rentagent123";

    private final String jwtSecret;
    private final String aesKey;
    private final String dbPassword;
    private final String captchaDevCode;

    public StartupConfigCheck(@Value("${rentagent.jwt-secret}") String jwtSecret,
                              @Value("${rentagent.aes-key}") String aesKey,
                              @Value("${spring.datasource.password}") String dbPassword,
                              @Value("${rentagent.captcha-dev-code:}") String captchaDevCode) {
        this.jwtSecret = jwtSecret;
        this.aesKey = aesKey;
        this.dbPassword = dbPassword;
        this.captchaDevCode = captchaDevCode;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> warnings = new ArrayList<>();
        if (DEFAULT_JWT_SECRET.equals(jwtSecret)) {
            warnings.add("JWT_SECRET 为仓库内置默认值：token 可被离线伪造（可伪造任意角色），"
                    + "对外提供访问前必须用环境变量 JWT_SECRET 覆盖");
        }
        if (DEFAULT_AES_KEY.equals(aesKey)) {
            warnings.add("AES_KEY 为仓库内置默认值：库中身份证密文等同明文，"
                    + "对外提供访问前必须用环境变量 AES_KEY 覆盖");
        }
        if (DEFAULT_DB_PASSWORD.equals(dbPassword)) {
            warnings.add("数据库口令为仓库内置默认值：请用 MYSQL_PASSWORD 覆盖");
        }
        if (captchaDevCode != null && !captchaDevCode.isBlank()) {
            warnings.add("验证码为固定演示值（captcha-dev-code=" + captchaDevCode
                    + "，任意手机号通用）：生产接入短信服务商后必须置空");
        }
        if (warnings.isEmpty()) {
            log.info("启动自检通过：JWT/AES/数据库凭据与验证码均已注入非默认值");
            return;
        }
        log.warn("启动自检发现 {} 项默认凭据（仅适用于本机演示，切勿对外暴露）：", warnings.size());
        for (String w : warnings) {
            log.warn("  - {}", w);
        }
    }
}
