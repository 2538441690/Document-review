package com.example.compliance.util;

import com.example.compliance.entity.TableInfo;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Word 文档解析工具
 * 基于 Apache POI 解析 .docx 文件，提取段落文本、格式信息、表格和图片
 */
public class WordParser {

    private static final Logger log = LoggerFactory.getLogger(WordParser.class);

    /**
     * 提取 Word 文档中的所有段落文本
     */
    public static List<String> extractParagraphs(File file) {
        List<String> paragraphs = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis)) {

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.trim().isEmpty()) {
                    paragraphs.add(text.trim());
                }
            }

            // 同时提取表格中的文本
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        String cellText = cell.getText();
                        if (cellText != null && !cellText.trim().isEmpty()) {
                            paragraphs.add(cellText.trim());
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("解析Word文档失败: {}", file.getName(), e);
        }
        return paragraphs;
    }

    /**
     * 获取段落字体信息（通过 XWPFRun 高层 API）
     */
    public static ParagraphFormatInfo getParagraphFormat(XWPFParagraph paragraph) {
        ParagraphFormatInfo info = new ParagraphFormatInfo();
        List<XWPFRun> runs = paragraph.getRuns();
        if (!runs.isEmpty()) {
            XWPFRun firstRun = runs.get(0);
            String fontName = firstRun.getFontName();
            if (fontName != null) {
                info.fontName = fontName;
            }
            Double fontSize = firstRun.getFontSizeAsDouble();
            if (fontSize != null && fontSize > 0) {
                info.fontSize = fontSize;
            }
        }
        if (paragraph.getSpacingBetween() > 0) {
            info.lineSpacing = paragraph.getSpacingBetween() / 240.0;
        }
        return info;
    }

    /**
     * 获取页面设置（页边距，单位：毫米）
     */
    public static PageMarginInfo getPageMargins(XWPFDocument document) {
        PageMarginInfo info = new PageMarginInfo();
        CTSectPr sectPr = document.getDocument().getBody().getSectPr();
        if (sectPr != null && sectPr.getPgMar() != null) {
            BigInteger top = (BigInteger) sectPr.getPgMar().getTop();
            BigInteger bottom = (BigInteger) sectPr.getPgMar().getBottom();
            BigInteger left = (BigInteger) sectPr.getPgMar().getLeft();
            BigInteger right = (BigInteger) sectPr.getPgMar().getRight();
            if (top != null) {
                info.topMargin = top.intValue() / 56.7;
            }
            if (bottom != null) {
                info.bottomMargin = bottom.intValue() / 56.7;
            }
            if (left != null) {
                info.leftMargin = left.intValue() / 56.7;
            }
            if (right != null) {
                info.rightMargin = right.intValue() / 56.7;
            }
        }
        return info;
    }

    /**
     * 提取 Word 文档中的所有表格
     */
    public static List<TableInfo> extractTables(File file) {
        List<TableInfo> tables = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis)) {

            int tableIndex = 0;
            for (XWPFTable table : document.getTables()) {
                TableInfo tableInfo = new TableInfo();
                tableInfo.setTableName("表格_" + (++tableIndex));
                tableInfo.setHasHeader(!table.getRows().isEmpty());

                List<List<String>> dataRows = new ArrayList<>();
                List<String> headers = new ArrayList<>();

                int rowCount = table.getRows().size();
                int expectedColumnCount = 0;

                for (int i = 0; i < rowCount; i++) {
                    XWPFTableRow row = table.getRows().get(i);
                    List<String> rowData = new ArrayList<>();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        rowData.add(cell.getText().trim());
                    }
                    if (i == 0) {
                        expectedColumnCount = rowData.size();
                        headers = rowData;
                    }
                    dataRows.add(rowData);
                }

                tableInfo.setRowCount(rowCount);
                tableInfo.setColumnCount(expectedColumnCount);
                tableInfo.setHeaders(headers);
                tableInfo.setDataRows(dataRows);
                tables.add(tableInfo);
            }

        } catch (Exception e) {
            log.error("提取Word表格失败: {}", file.getName(), e);
        }
        return tables;
    }

    /**
     * 段落格式信息
     */
    public static class ParagraphFormatInfo {
        public String fontName;
        public double fontSize;
        public double lineSpacing;
    }

    /**
     * 页面边距信息
     */
    public static class PageMarginInfo {
        public double topMargin;
        public double bottomMargin;
        public double leftMargin;
        public double rightMargin;
    }
}