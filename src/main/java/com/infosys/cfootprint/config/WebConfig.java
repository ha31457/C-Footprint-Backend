package com.infosys.cfootprint.config;

import com.infosys.cfootprint.security.TemporaryPasswordInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private TemporaryPasswordInterceptor temporaryPasswordInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(temporaryPasswordInterceptor)
                .addPathPatterns("/**"); // Intercept all request paths
    }
}
