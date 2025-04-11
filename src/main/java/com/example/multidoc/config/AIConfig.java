package com.example.multidoc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;

@Configuration
public class AIConfig {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;
    
    @Value("${spring.ai.openai.chat.model}")
    private String model;
    
    @Value("${spring.ai.openai.chat.max-tokens}")
    private Integer maxTokens;
    
    @Value("${spring.ai.openai.chat.temperature}")
    private Double temperature;
    
    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;
    
    @Bean
    public OpenAiApi openAiApi() {
        return new OpenAiApi(baseUrl, apiKey);
    }
    
    @Bean
    public OpenAiChatClient openAiChatClient(OpenAiApi openAiApi) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
            .withModel(model)
            .withTemperature(temperature.floatValue())
            .withMaxTokens(maxTokens)
            .build();
        
        return new OpenAiChatClient(openAiApi, options);
    }
} 