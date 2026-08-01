package com.aisuite.config;

import com.aisuite.service.SessionService;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final SessionService sessionService;

    public WebConfig(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(new AuthInterceptor(sessionService))
                // Protect all API routes except auth endpoints and static files
                .addPathPatterns("/chat", "/currency-chat", "/history/**",
                        "/ingest/**", "/user/**")
                .excludePathPatterns("/auth/**");
    }

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS");
    }
}
