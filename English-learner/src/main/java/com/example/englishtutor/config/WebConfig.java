package com.example.englishtutor.config;
import lombok.RequiredArgsConstructor;import org.springframework.context.annotation.Configuration;import org.springframework.web.servlet.config.annotation.*;
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer { private final AuthInterceptor authInterceptor; @Override public void addInterceptors(InterceptorRegistry r){ r.addInterceptor(authInterceptor).addPathPatterns("/api/**"); } }
