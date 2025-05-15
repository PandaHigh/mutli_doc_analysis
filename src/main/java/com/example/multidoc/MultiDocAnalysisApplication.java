package com.example.multidoc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
public class MultiDocAnalysisApplication {

    public static void main(String[] args) {
        SpringApplication.run(MultiDocAnalysisApplication.class, args);
    }


    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                       .allowedOriginPatterns("*")
                       .allowedMethods("*")
                       .allowedHeaders("*")
                       .allowCredentials(true);
            }
            
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                registry.addResourceHandler("/webjars/**")
                        .addResourceLocations("classpath:/META-INF/resources/webjars/");
                registry.addResourceHandler("/css/**")
                        .addResourceLocations("classpath:/static/css/");
                registry.addResourceHandler("/js/**")
                        .addResourceLocations("classpath:/static/js/");
            }
        };
    }
} 