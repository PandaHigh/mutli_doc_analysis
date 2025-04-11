package com.example.multidoc.util;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class ExcelProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(ExcelProcessor.class);
    private final DocumentParser documentParser;
    
    // 中文字符的正则表达式
    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]+");
    
    // 最小中文字符比例（超过这个比例才认为是中文描述）
    private static final double MIN_CHINESE_RATIO = 0.3;

    public ExcelProcessor() {
        this.documentParser = new ApachePoiDocumentParser();
    }

    /**
     * 将Excel转换为Markdown格式
     * @param filePath Excel文件路径
     * @return Markdown文本
     */
    public String convertExcelToMarkdown(String filePath) {
        try (FileInputStream fis = new FileInputStream(new File(filePath))) {
            Document document = documentParser.parse(fis);
            return document.text();
        } catch (IOException e) {
            logger.error("转换Excel到Markdown失败: " + filePath, e);
            return "";
        }
    }

    /**
     * 检查文本是否包含足够比例的中文字符
     * @param text 要检查的文本
     * @return 是否包含足够的中文字符
     */
    private boolean containsChineseCharacters(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        
        // 计算中文字符数量
        int chineseCount = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseCount++;
            }
        }
        
        // 计算中文字符比例
        double ratio = (double) chineseCount / text.length();
        return ratio >= MIN_CHINESE_RATIO;
    }

    /**
     * 提取Excel中的所有要素（仅保留中文描述）
     * @param filePath Excel文件路径
     * @return 要素信息列表
     */
    public List<ElementInfo> extractFields(String filePath) {
        List<ElementInfo> elements = new ArrayList<>();
        
        try (FileInputStream fis = new FileInputStream(new File(filePath));
             Workbook workbook = new XSSFWorkbook(fis)) {
            
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                String sheetName = sheet.getSheetName();
                
                // 获取所有合并单元格的信息
                Map<String, CellRangeAddress> mergedRegions = new HashMap<>();
                for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
                    CellRangeAddress region = sheet.getMergedRegion(i);
                    mergedRegions.put(region.getFirstRow() + "," + region.getFirstColumn(), region);
                }
                
                // 遍历所有行
                for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row == null) continue;
                    
                    // 遍历所有列
                    for (int colIndex = row.getFirstCellNum(); colIndex < row.getLastCellNum(); colIndex++) {
                        Cell cell = row.getCell(colIndex);
                        if (cell == null) continue;
                        
                        // 获取单元格值
                        String value = getCellValueAsString(cell);
                        if (value.isEmpty()) continue;
                        
                        // 检查是否包含足够的中文字符
                        if (!containsChineseCharacters(value)) {
                            continue;
                        }
                        
                        // 检查是否是合并单元格的起始位置
                        String cellKey = rowIndex + "," + colIndex;
                        CellRangeAddress mergedRegion = mergedRegions.get(cellKey);
                        
                        // 创建要素信息
                        ElementInfo element = new ElementInfo();
                        element.setSheetName(sheetName);
                        element.setRowIndex(rowIndex);
                        element.setColumnIndex(colIndex);
                        element.setValue(value);
                        
                        // 处理合并单元格
                        if (mergedRegion != null) {
                            element.setMerged(true);
                            element.setMergedRowSpan(mergedRegion.getLastRow() - mergedRegion.getFirstRow() + 1);
                            element.setMergedColSpan(mergedRegion.getLastColumn() - mergedRegion.getFirstColumn() + 1);
                        }
                        
                        // 获取单元格样式信息
                        CellStyle style = cell.getCellStyle();
                        Font font = workbook.getFontAt(style.getFontIndex());
                        element.setFontBold(font.getBold());
                        element.setFontItalic(font.getItalic());
                        element.setFontSize(font.getFontHeightInPoints());
                        
                        // 获取单元格的公式（如果有）
                        if (cell.getCellType() == CellType.FORMULA) {
                            element.setFormula(cell.getCellFormula());
                        }
                        
                        // 获取单元格的批注（如果有）
                        Comment comment = cell.getCellComment();
                        if (comment != null) {
                            String commentText = comment.getString().getString();
                            // 只保留包含中文的批注
                            if (containsChineseCharacters(commentText)) {
                                element.setComment(commentText);
                            }
                        }
                        
                        elements.add(element);
                    }
                }
            }
            
        } catch (IOException e) {
            logger.error("提取Excel要素信息失败: " + filePath, e);
        }
        
        return elements;
    }

    /**
     * 获取单元格的值并转换为字符串
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().replace("|", "\\|"); // 转义Markdown表格分隔符
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toString();
                } else {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    try {
                        return cell.getStringCellValue();
                    } catch (Exception ex) {
                        return cell.getCellFormula();
                    }
                }
            case BLANK:
                return "";
            default:
                return "";
        }
    }

    /**
     * Excel要素信息类
     */
    public static class ElementInfo {
        private String sheetName;
        private int rowIndex;
        private int columnIndex;
        private String value;
        private boolean merged;
        private int mergedRowSpan;
        private int mergedColSpan;
        private boolean fontBold;
        private boolean fontItalic;
        private int fontSize;
        private String formula;
        private String comment;

        public String getSheetName() {
            return sheetName;
        }

        public void setSheetName(String sheetName) {
            this.sheetName = sheetName;
        }

        public int getRowIndex() {
            return rowIndex;
        }

        public void setRowIndex(int rowIndex) {
            this.rowIndex = rowIndex;
        }

        public int getColumnIndex() {
            return columnIndex;
        }

        public void setColumnIndex(int columnIndex) {
            this.columnIndex = columnIndex;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public boolean isMerged() {
            return merged;
        }

        public void setMerged(boolean merged) {
            this.merged = merged;
        }

        public int getMergedRowSpan() {
            return mergedRowSpan;
        }

        public void setMergedRowSpan(int mergedRowSpan) {
            this.mergedRowSpan = mergedRowSpan;
        }

        public int getMergedColSpan() {
            return mergedColSpan;
        }

        public void setMergedColSpan(int mergedColSpan) {
            this.mergedColSpan = mergedColSpan;
        }

        public boolean isFontBold() {
            return fontBold;
        }

        public void setFontBold(boolean fontBold) {
            this.fontBold = fontBold;
        }

        public boolean isFontItalic() {
            return fontItalic;
        }

        public void setFontItalic(boolean fontItalic) {
            this.fontItalic = fontItalic;
        }

        public int getFontSize() {
            return fontSize;
        }

        public void setFontSize(int fontSize) {
            this.fontSize = fontSize;
        }

        public String getFormula() {
            return formula;
        }

        public void setFormula(String formula) {
            this.formula = formula;
        }

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }

        @Override
        public String toString() {
            return "ElementInfo{" +
                    "sheetName='" + sheetName + '\'' +
                    ", rowIndex=" + rowIndex +
                    ", columnIndex=" + columnIndex +
                    ", value='" + value + '\'' +
                    ", merged=" + merged +
                    ", mergedRowSpan=" + mergedRowSpan +
                    ", mergedColSpan=" + mergedColSpan +
                    ", fontBold=" + fontBold +
                    ", fontItalic=" + fontItalic +
                    ", fontSize=" + fontSize +
                    ", formula='" + formula + '\'' +
                    ", comment='" + comment + '\'' +
                    '}';
        }
    }
} 