package com.example.multidoc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.HiddenHttpMethodFilter;

@Configuration
public class WebConfig {
    
    /**
     * 启用HTTP方法转换过滤器，允许通过_method参数转换POST请求为其他HTTP方法
     * 例如：通过表单的_method=DELETE字段，将POST请求转换为DELETE请求
     */
    @Bean
    public HiddenHttpMethodFilter hiddenHttpMethodFilter() {
        return new HiddenHttpMethodFilter();
    }
}
