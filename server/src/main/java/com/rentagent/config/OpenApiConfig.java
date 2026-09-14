package com.rentagent.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI rentAgentOpenApi() {
        return new OpenAPI().info(new Info()
                .title("RentAgent API")
                .description("RentAgent —— 基于 AI 智能体的房屋租赁系统（Spring Boot 3 + MyBatis-Plus + LangChain4j）")
                .version("v1"));
    }
}
