package com.example.compliance.service;

import com.example.compliance.entity.ReviewIssue;
import com.example.compliance.util.DFAFilter;
import com.example.compliance.util.ImageParser;
import com.example.compliance.util.OCRUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 图片合规校验服务
 * 功能：
 * 1. 基础格式校验：图片格式、分辨率、尺寸、DPI
 * 2. OCR 文字提取：使用 Tess4J 调用 Tesseract 引擎
 * 3. 敏感信息检测：将 OCR 结果送入 DFA 模块进行二次校验
 */
@Service
public class ImageComplianceService {

    private static final Logger log = LoggerFactory.getLogger(ImageComplianceService.class);

    private final TextSecurityService textSecurityService;

    private OCRUtil ocrUtil;

    @Value("${compliance.image.allowed-formats:PNG,JPEG,JPG,BMP}")
    private String allowedFormatsStr;

    @Value("${compliance.image.min-width:200}")
    private int minWidth;

    @Value("${compliance.image.min-height:200}")
    private int minHeight;

    @Value("${compliance.image.min-dpi:150}")
    private int minDpi;

    @Value("${compliance.image.max-size-mb:10}")
    private int maxSizeMb;

    @Value("${compliance.tesseract.data-path:C:/Program Files/Tesseract-OCR/tessdata}")
    private String tessDataPath;

    @Value("${compliance.tesseract.language:chi_sim+eng}")
    private String tessLanguage;

    private String[] allowedFormats;

    private boolean ocrAvailable = false;

    public ImageComplianceService(TextSecurityService textSecurityService) {
        this.textSecurityService = textSecurityService;
    }

    /**
     * 初始化图片校验服务
     */
    @PostConstruct
    public void init() {
        this.allowedFormats = allowedFormatsStr.split(",");
        try {
            String resolvedPath = Paths.get(tessDataPath).toAbsolutePath().toString();
            this.ocrUtil = new OCRUtil(resolvedPath, tessLanguage);
            this.ocrAvailable = true;
            log.info("Tesseract OCR 初始化完成，数据路径: {}，语言: {}", resolvedPath, tessLanguage);
        } catch (Exception e) {
            log.warn("Tesseract OCR 初始化失败，OCR功能不可用: {}", e.getMessage());
            this.ocrAvailable = false;
        }
    }

    /**
     * 校验单个图片文件
     *
     * @param imageFile 图片文件
     * @return 合规问题列表
     */
    public List<ReviewIssue> checkImage(File imageFile) {
        List<ReviewIssue> issues = new ArrayList<>(checkImageFormat(imageFile));

        // 2. OCR 文字提取 + 敏感词检测
        if (ocrAvailable) {
            issues.addAll(checkImageSensitiveContent(imageFile));
        }

        return issues;
    }

    /**
     * 校验图片基础格式
     */
    private List<ReviewIssue> checkImageFormat(File imageFile) {
        List<ReviewIssue> issues = new ArrayList<>();
        ImageParser.ImageMetaInfo metaInfo = ImageParser.readImageMeta(imageFile);

        // 格式检查
        if (!ImageParser.isValidFormat(metaInfo.format, allowedFormats)) {
            issues.add(ReviewIssue.builder()
                    .severity(ReviewIssue.Severity.ERROR)
                    .category(ReviewIssue.Category.IMAGE_FORMAT)
                    .location(imageFile.getName())
                    .description(String.format("图片格式不符合规范，当前: %s，允许: %s",
                            metaInfo.format, String.join(", ", allowedFormats)))
                    .suggestion("请使用允许的图片格式: " + String.join(", ", allowedFormats))
                    .build());
        }

        // 分辨率检查
        if (metaInfo.width > 0 && metaInfo.height > 0) {
            if (!ImageParser.isValidResolution(metaInfo.width, metaInfo.height, minWidth, minHeight)) {
                issues.add(ReviewIssue.builder()
                        .severity(ReviewIssue.Severity.WARNING)
                        .category(ReviewIssue.Category.IMAGE_FORMAT)
                        .location(imageFile.getName())
                        .description(String.format("图片分辨率过低，当前: %dx%d，最低要求: %dx%d",
                                metaInfo.width, metaInfo.height, minWidth, minHeight))
                        .suggestion("请使用分辨率不低于 " + minWidth + "x" + minHeight + " 的图片")
                        .build());
            }
        }

        // DPI 检查
        if (metaInfo.dpi > 0 && !ImageParser.isValidDpi(metaInfo.dpi, minDpi)) {
            issues.add(ReviewIssue.builder()
                    .severity(ReviewIssue.Severity.WARNING)
                    .category(ReviewIssue.Category.IMAGE_FORMAT)
                    .location(imageFile.getName())
                    .description(String.format("图片DPI不符合规范，当前: %d，最低要求: %d",
                            metaInfo.dpi, minDpi))
                    .suggestion("请使用DPI不低于 " + minDpi + " 的图片")
                    .build());
        }

        // 文件大小检查
        long maxSizeBytes = maxSizeMb * 1024L * 1024L;
        if (metaInfo.fileSize > maxSizeBytes) {
            issues.add(ReviewIssue.builder()
                    .severity(ReviewIssue.Severity.WARNING)
                    .category(ReviewIssue.Category.IMAGE_FORMAT)
                    .location(imageFile.getName())
                    .description(String.format("图片文件过大，当前: %.2fMB，最大允许: %dMB",
                            metaInfo.fileSize / (1024.0 * 1024.0), maxSizeMb))
                    .suggestion("请压缩图片，减小文件大小")
                    .build());
        }

        return issues;
    }

    /**
     * OCR 提取文字并进行敏感词检测
     */
    private List<ReviewIssue> checkImageSensitiveContent(File imageFile) {
        List<ReviewIssue> issues = new ArrayList<>();

        try {
            String ocrText = ocrUtil.extractText(imageFile);
            if (ocrText != null && !ocrText.trim().isEmpty()) {
                log.debug("OCR提取文字: {}... (共{}字符)",
                        ocrText.substring(0, Math.min(50, ocrText.length())),
                        ocrText.length());

                // 将 OCR 结果送入 DFA 模块进行二次校验
                DFAFilter dfaFilter = textSecurityService.getDfaFilter();
                List<DFAFilter.MatchResult> matches = dfaFilter.scan(ocrText);

                for (DFAFilter.MatchResult match : matches) {
                    issues.add(ReviewIssue.builder()
                            .severity(ReviewIssue.Severity.ERROR)
                            .category(ReviewIssue.Category.IMAGE_SENSITIVE)
                            .location("图片: " + imageFile.getName())
                            .description("图片OCR文字中检测到" + match.category() + ": " + match.word())
                            .suggestion("图片中包含敏感信息，请修改或删除图片")
                            .build());
                }
            }
        } catch (Exception e) {
            log.error("OCR敏感词检测失败: {}", imageFile.getName(), e);
        }

        return issues;
    }

    /**
     * 批量校验多个图片
     */
    public List<ReviewIssue> checkImages(List<File> imageFiles) {
        List<ReviewIssue> allIssues = new ArrayList<>();
        for (File imageFile : imageFiles) {
            allIssues.addAll(checkImage(imageFile));
        }
        return allIssues;
    }
}