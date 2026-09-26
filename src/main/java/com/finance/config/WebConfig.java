package com.finance.config;

import com.finance.financeplus.web.FinancePlusAccessInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final FinancePlusAccessInterceptor financePlusAccessInterceptor;

    public WebConfig(FinancePlusAccessInterceptor financePlusAccessInterceptor) {
        this.financePlusAccessInterceptor = financePlusAccessInterceptor;
    }

    @Override
    public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        registry.addInterceptor(financePlusAccessInterceptor)
                .addPathPatterns("/finance-plus/**")
                .excludePathPatterns("/finance-plus/locked", "/finance-plus/unlock");
    }

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:data/uploads/");
        registry.addResourceHandler("/locales/**")
                .addResourceLocations("classpath:/static/locales/");
    }
}
