package com.example.compliance.service;

import com.example.compliance.entity.ReviewIssue;
import com.example.compliance.util.WordParser;
import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 文本格式校验服务
 * 基于 Apache POI 校验 Word 文档的字体、字号、行距、页边距是否符合规范。
 * 所有格式规范均可通过 application.yml 配置。
 */
@Service
public class TextFormatService {

    private static final Logger log = LoggerFactory.getLogger(TextFormatService.class);

    @Value("${compliance.format.font-name:宋体}")
    private String requiredFontName;

    @Value("${compliance.format.font-size-min:10.5}")
    private double fontSizeMin;

    @Value("${compliance.format.font-size-max:16.0}")
    private double fontSizeMax;

    @Value("${compliance.format.line-spacing-min:1.15}")
    private double lineSpacingMin;

    @Value("${compliance.format.line-spacing-max:1.5}")
    private double lineSpacingMax;

    @Value("${compliance.format.margin-top-mm:25.4}")
    private double marginTop;

    @Value("${compliance.format.margin-bottom-mm:25.4}")
    private double marginBottom;

    @Value("${compliance.format.margin-left-mm:31.7}")
    private double marginLeft;

    @Value("${compliance.format.margin-right-mm:31.7}")
    private double marginRight;

    @Value("${compliance.format.margin-tolerance-mm:2.0}")
    private double marginTolerance;

    /**
     * 校验 Word 文档的文本格式
     */
    public List<ReviewIssue> checkFormat(File wordFile) {
        List<ReviewIssue> issues = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(wordFile);
             XWPFDocument document = new XWPFDocument(fis)) {

            // 校验页边距
            issues.addAll(checkPageMargins(document));

            // 校验段落格式
            issues.addAll(checkParagraphFormat(document));

        } catch (Exception e) {
            log.error("文本格式校验失败: {}", wordFile.getName(), e);
            issues.add(ReviewIssue.builder()
                    .severity(ReviewIssue.Severity.ERROR)
                    .category(ReviewIssue.Category.TEXT_FORMAT)
                    .description("文档格式解析失败: " + e.getMessage())
                    .suggestion("请检查文档是否损坏")
                    .build());
        }

        return issues;
    }

    /**
     * 校验页边距
     */
    private List<ReviewIssue> checkPageMargins(XWPFDocument document) {
        List<ReviewIssue> issues = new ArrayList<>();
        WordParser.PageMarginInfo margins = WordParser.getPageMargins(document);

        if (Math.abs(margins.topMargin - marginTop) > marginTolerance) {
            issues.add(ReviewIssue.builder()
                    .severity(ReviewIssue.Severity.WARNING)
                    .category(ReviewIssue.Category.TEXT_FORMAT)
                    .location("页面上边距")
                    .description(String.format("上边距不符合规范，当前: %.1fmm，要求: %.1fmm",
                            margins.topMargin, marginTop))
                    .suggestion(String.format("请将上边距调整为 %.1fmm", marginTop))
                    .build());
        }

        if (Math.abs(margins.bottomMargin - marginBottom) > marginTolerance) {
            issues.add(ReviewIssue.builder()
                    .severity(ReviewIssue.Severity.WARNING)
                    .category(ReviewIssue.Category.TEXT_FORMAT)
                    .location("页面下边距")
                    .description(String.format("下边距不符合规范，当前: %.1fmm，要求: %.1fmm",
                            margins.bottomMargin, marginBottom))
                    .suggestion(String.format("请将下边距调整为 %.1fmm", marginBottom))
                    .build());
        }

        if (Math.abs(margins.leftMargin - marginLeft) > marginTolerance) {
            issues.add(ReviewIssue.builder()
                    .severity(ReviewIssue.Severity.WARNING)
                    .category(ReviewIssue.Category.TEXT_FORMAT)
                    .location("页面左边距")
                    .description(String.format("左边距不符合规范，当前: %.1fmm，要求: %.1fmm",
                            margins.leftMargin, marginLeft))
                    .suggestion(String.format("请将左边距调整为 %.1fmm", marginLeft))
                    .build());
        }

        if (Math.abs(margins.rightMargin - marginRight) > marginTolerance) {
            issues.add(ReviewIssue.builder()
                    .severity(ReviewIssue.Severity.WARNING)
                    .category(ReviewIssue.Category.TEXT_FORMAT)
                    .location("页面右边距")
                    .description(String.format("右边距不符合规范，当前: %.1fmm，要求: %.1fmm",
                            margins.rightMargin, marginRight))
                    .suggestion(String.format("请将右边距调整为 %.1fmm", marginRight))
                    .build());
        }

        return issues;
    }

    /**
     * 校验段落格式
     */
    private List<ReviewIssue> checkParagraphFormat(XWPFDocument document) {
        List<ReviewIssue> issues = new ArrayList<>();
        List<XWPFParagraph> paragraphs = document.getParagraphs();

        for (int i = 0; i < paragraphs.size(); i++) {
            XWPFParagraph paragraph = paragraphs.get(i);
            String text = paragraph.getText();
            if (text == null || text.trim().isEmpty()) {
                continue;
            }

            WordParser.ParagraphFormatInfo formatInfo = WordParser.getParagraphFormat(paragraph);
            String location = "第" + (i + 1) + "段";

            // 校验字体
            if (formatInfo.fontName != null && !formatInfo.fontName.isEmpty()) {
                if (!formatInfo.fontName.equals(requiredFontName)) {
                    issues.add(ReviewIssue.builder()
                            .severity(ReviewIssue.Severity.WARNING)
                            .category(ReviewIssue.Category.TEXT_FORMAT)
                            .location(location)
                            .description(String.format("字体不符合规范，当前: %s，要求: %s",
                                    formatInfo.fontName, requiredFontName))
                            .suggestion("请将字体修改为" + requiredFontName)
                            .build());
                }
            }

            // 校验字号
            if (formatInfo.fontSize > 0) {
                if (formatInfo.fontSize < fontSizeMin || formatInfo.fontSize > fontSizeMax) {
                    issues.add(ReviewIssue.builder()
                            .severity(ReviewIssue.Severity.WARNING)
                            .category(ReviewIssue.Category.TEXT_FORMAT)
                            .location(location)
                            .description(String.format("字号不符合规范，当前: %.1fpt，要求: %.1f-%.1fpt",
                                    formatInfo.fontSize, fontSizeMin, fontSizeMax))
                            .suggestion("请调整字号到规范范围内")
                            .build());
                }
            }

            // 校验行距
            if (formatInfo.lineSpacing > 0) {
                if (formatInfo.lineSpacing < lineSpacingMin || formatInfo.lineSpacing > lineSpacingMax) {
                    issues.add(ReviewIssue.builder()
                            .severity(ReviewIssue.Severity.WARNING)
                            .category(ReviewIssue.Category.TEXT_FORMAT)
                            .location(location)
                            .description(String.format("行距不符合规范，当前: %.2f倍，要求: %.2f-%.2f倍",
                                    formatInfo.lineSpacing, lineSpacingMin, lineSpacingMax))
                            .suggestion("请调整行距到规范范围内")
                            .build());
                }
            }
        }

        return issues;
    }
}