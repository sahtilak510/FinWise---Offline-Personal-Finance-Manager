package com.finance.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AppLockInterceptor appLockInterceptor;

    public WebMvcConfig(AppLockInterceptor appLockInterceptor) {
        this.appLockInterceptor = appLockInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(appLockInterceptor).addPathPatterns("/**").excludePathPatterns(
                "/css/**", "/js/**", "/vendor/**", "/locales/**", "/images/**", "/favicon.ico", "/app-lock/**");
    }
}
