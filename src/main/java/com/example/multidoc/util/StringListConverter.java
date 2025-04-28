package com.example.multidoc.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {
    
    private static final Logger logger = LoggerFactory.getLogger(StringListConverter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "[]";
        }
        
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            logger.error("Error converting list to JSON", e);
            return "[]";
        }
    }
    
    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty() || "[]".equals(dbData)) {
            return new ArrayList<>();
        }
        
        try {
            return objectMapper.readValue(dbData, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            logger.error("Error converting JSON to list", e);
            return new ArrayList<>();
        }
    }
} 