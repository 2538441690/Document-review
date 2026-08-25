package com.example.compliance.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 文档解析工具
 * 基于 Apache PDFBox 提取 PDF 中的文本内容
 */
public class PDFParser {

    private static final Logger log = LoggerFactory.getLogger(PDFParser.class);

    /**
     * 提取 PDF 文档中的所有文本
     */
    public static List<String> extractText(File file) {
        List<String> paragraphs = new ArrayList<>();
        try (PDDocument document = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);

            // 按段落拆分
            String[] lines = text.split("\\n\\s*\\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    paragraphs.add(trimmed);
                }
            }
        } catch (IOException e) {
            log.error("解析PDF文档失败: {}", file.getName(), e);
        }
        return paragraphs;
    }
}