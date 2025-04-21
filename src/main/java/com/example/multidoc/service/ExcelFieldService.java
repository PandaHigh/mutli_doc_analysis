package com.example.multidoc.service;

import com.example.multidoc.model.ExcelField;
import com.example.multidoc.model.AnalysisTask;
import com.example.multidoc.util.ExcelProcessor.ElementInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class ExcelFieldService {

    @Transactional
    public void saveFields(AnalysisTask task, List<ElementInfo> elements) {
        for (ElementInfo element : elements) {
            ExcelField field = new ExcelField();
            field.setTask(task);
            field.setTableName(element.getTableName());
            field.setFieldName(element.getValue());
            field.setFieldType("STRING"); // 默认类型
            field.setDescription("从Excel中提取的字段");
        }
    }
} 