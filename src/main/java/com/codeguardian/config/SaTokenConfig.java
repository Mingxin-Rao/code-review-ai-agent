package com.codeguardian.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token interceptor configuration
 *
 * <p>Uniformly intercept sensitive paths to enforce login checks:
 * /admin/**, /api/** (excluding authentication endpoints), /review/**.
 * Public endpoints such as static resources and health checks are allowed through.</p>
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /**
     * Register the Sa-Token interceptor and declare path-matching rules
     *
     * @param registry the Spring MVC interceptor registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handler -> {
            StpUtil.checkLogin();
        }))
        .addPathPatterns("/admin/**", "/api/**", "/review/**")
        .excludePathPatterns(
                "/login",
                "/api/auth/**",
                "/logout",
                "/actuator/**",
                "/error",
                "/css/**",
                "/js/**",
                "/images/**"
        );
    }
}
