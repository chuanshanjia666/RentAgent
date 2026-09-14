package com.rentagent.config;

import com.rentagent.security.JwtAuthFilter;
import com.rentagent.security.RoleInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RoleInterceptor roleInterceptor;
    private final JwtAuthFilter jwtAuthFilter;
    private final String uploadDir;
    private final String corsOrigins;

    public WebConfig(RoleInterceptor roleInterceptor, JwtAuthFilter jwtAuthFilter,
                     @Value("${rentagent.upload-dir}") String uploadDir,
                     @Value("${rentagent.cors-origins}") String corsOrigins) {
        this.roleInterceptor = roleInterceptor;
        this.jwtAuthFilter = jwtAuthFilter;
        this.uploadDir = uploadDir;
        this.corsOrigins = corsOrigins;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtFilterRegistration() {
        FilterRegistrationBean<JwtAuthFilter> reg = new FilterRegistrationBean<>(jwtAuthFilter);
        reg.addUrlPatterns("/api/*");
        reg.setOrder(1);
        return reg;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(roleInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(corsOrigins.split(","))
                .allowedMethods("*")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + Paths.get(uploadDir).toAbsolutePath().normalize() + "/");
    }
}
