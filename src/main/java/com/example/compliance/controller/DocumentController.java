package com.example.compliance.controller;

import com.example.compliance.dto.ValidationResponse;
import com.example.compliance.entity.ReviewIssue;
import com.example.compliance.entity.ValidationResult;
import com.example.compliance.service.*;
import com.example.compliance.util.PDFParser;
import com.example.compliance.util.WordParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 文档审查控制器
 * 接收文件上传请求，根据文件类型路由到对应的审查服务，
 * 使用 CompletableFuture 并行执行各审查器，汇总结果返回。
 */
@RestController
@RequestMapping("/api/document")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    private final TextSecurityService textSecurityService;
    private final GrammarService grammarService;
    private final TextFormatService textFormatService;
    private final ImageComplianceService imageComplianceService;
    private final TableFormatService tableFormatService;
    private final ExecutorService reviewExecutor;

    public DocumentController(
            TextSecurityService textSecurityService,
            GrammarService grammarService,
            TextFormatService textFormatService,
            ImageComplianceService imageComplianceService,
            TableFormatService tableFormatService,
            @Qualifier("reviewExecutor") ExecutorService reviewExecutor) {
        this.textSecurityService = textSecurityService;
        this.grammarService = grammarService;
        this.textFormatService = textFormatService;
        this.imageComplianceService = imageComplianceService;
        this.tableFormatService = tableFormatService;
        this.reviewExecutor = reviewExecutor;
    }

    private static final Set<String> WORD_EXTENSIONS = Set.of("docx", "doc");
    private static final Set<String> EXCEL_EXTENSIONS = Set.of("xlsx", "xls");
    private static final Set<String> PDF_EXTENSIONS = Set.of("pdf");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "bmp", "gif", "tiff");

    /**
     * 上传文档并进行全面审查
     *
     * @param file 上传的文件
     * @return 审查结果
     */
    @PostMapping(value = "/review", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ValidationResponse> reviewDocument(@RequestParam("file") MultipartFile file) {
        long startTime = System.currentTimeMillis();
        String originalFilename = file.getOriginalFilename();
        String documentId = UUID.randomUUID().toString().substring(0, 8);

        log.info("收到审查请求: {} (ID: {})", originalFilename, documentId);

        String extension = getFileExtension(originalFilename);
        try {
            Path tempFile = Files.createTempFile("review_", "." + extension);
            try {
                Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);

                File reviewFile = tempFile.toFile();
                String ext = extension.toLowerCase();

                ValidationResult result = reviewByFileType(reviewFile, ext, documentId, originalFilename);
                ValidationResponse response = buildResponse(result, System.currentTimeMillis() - startTime);

                log.info("审查完成: {}，耗时: {}ms，问题数: {}，合规: {}",
                        originalFilename, response.getDurationMs(),
                        response.getTotalIssues(), response.isCompliant());

                return ResponseEntity.ok(response);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (IOException e) {
            log.error("文件处理失败", e);
            return ResponseEntity.badRequest().body(
                    ValidationResponse.builder()
                            .documentId(documentId)
                            .fileName(originalFilename)
                            .compliant(false)
                            .totalIssues(0)
                            .errorCount(0)
                            .warningCount(0)
                            .infoCount(0)
                            .durationMs(System.currentTimeMillis() - startTime)
                            .issues(Collections.emptyList())
                            .summary("文件处理失败: " + e.getMessage())
                            .build());
        }
    }

    /**
     * 根据文件类型执行审查
     */
    private ValidationResult reviewByFileType(File file, String extension, String documentId, String fileName) {
        ValidationResult result = ValidationResult.builder()
                .documentId(documentId)
                .fileName(fileName)
                .compliant(true)
                .reviewTime(LocalDateTime.now())
                .build();

        // Word 文档：文本安全 + 语法 + 格式 + 表格 + 图片
        if (WORD_EXTENSIONS.contains(extension)) {
            reviewWordDocument(file, result);
        }
        // Excel 文档：表格格式 + 文本安全
        else if (EXCEL_EXTENSIONS.contains(extension)) {
            reviewExcelDocument(file, result);
        }
        // PDF 文档：文本安全 + 语法
        else if (PDF_EXTENSIONS.contains(extension)) {
            reviewPdfDocument(file, result);
        }
        // 图片：图片格式 + OCR + 敏感词
        else if (IMAGE_EXTENSIONS.contains(extension)) {
            reviewImageFile(file, result);
        }
        // 不支持的文件类型
        else {
            result.addIssue(ReviewIssue.builder()
                    .severity(ReviewIssue.Severity.ERROR)
                    .category(ReviewIssue.Category.TEXT_FORMAT)
                    .description("不支持的文件类型: " + extension)
                    .suggestion("请上传 Word(.docx)、Excel(.xlsx)、PDF 或图片文件")
                    .build());
        }

        return result;
    }

    /**
     * 审查 Word 文档（并行执行所有审查器）
     */
    private void reviewWordDocument(File file, ValidationResult result) {
        // 提取段落文本（串行，因为后续依赖此结果）
        List<String> paragraphs = WordParser.extractParagraphs(file);

        // 并行执行各审查器
        CompletableFuture<List<ReviewIssue>> securityFuture = CompletableFuture.supplyAsync(
                () -> textSecurityService.checkSensitive(paragraphs), reviewExecutor);

        CompletableFuture<List<ReviewIssue>> grammarFuture = CompletableFuture.supplyAsync(
                () -> checkAllParagraphsGrammar(paragraphs), reviewExecutor);

        CompletableFuture<List<ReviewIssue>> formatFuture = CompletableFuture.supplyAsync(
                () -> textFormatService.checkFormat(file), reviewExecutor);

        CompletableFuture<List<ReviewIssue>> tableFuture = CompletableFuture.supplyAsync(
                () -> tableFormatService.checkWordTable(file), reviewExecutor);

        CompletableFuture<List<ReviewIssue>> imageFuture = CompletableFuture.supplyAsync(
                () -> {
                    List<File> imageFiles = new ArrayList<>();
                    // 图片提取和处理
                    return imageComplianceService.checkImages(imageFiles);
                }, reviewExecutor);

        // 等待所有审查完成，汇总结果
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                securityFuture, grammarFuture, formatFuture, tableFuture, imageFuture);

        // 设置超时时间
        try {
            allFutures.get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("审查超时或异常", e);
        }

        // 汇总结果
        addFutureResults(result, securityFuture, "文本安全审查");
        addFutureResults(result, grammarFuture, "语法审查");
        addFutureResults(result, formatFuture, "格式审查");
        addFutureResults(result, tableFuture, "表格审查");
        addFutureResults(result, imageFuture, "图片审查");
    }

    /**
     * 审查 Excel 文档
     */
    private void reviewExcelDocument(File file, ValidationResult result) {
        CompletableFuture<List<ReviewIssue>> tableFuture = CompletableFuture.supplyAsync(
                () -> tableFormatService.checkExcelTable(file), reviewExecutor);

        // 提取 Excel 单元格文本进行敏感词检查
        CompletableFuture<List<ReviewIssue>> securityFuture = CompletableFuture.supplyAsync(
                () -> {
                    List<String> cellTexts = extractExcelCellTexts(file);
                    return textSecurityService.checkSensitive(cellTexts);
                }, reviewExecutor);

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(tableFuture, securityFuture);
        try {
            allFutures.get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Excel审查超时或异常", e);
        }

        addFutureResults(result, tableFuture, "表格审查");
        addFutureResults(result, securityFuture, "文本安全审查");
    }

    /**
     * 提取 Excel 文件中所有单元格的文本内容
     */
    private List<String> extractExcelCellTexts(File excelFile) {
        List<String> cellTexts = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(excelFile);
             org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(fis)) {
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                org.apache.poi.xssf.usermodel.XSSFSheet sheet = workbook.getSheetAt(s);
                for (org.apache.poi.ss.usermodel.Row row : sheet) {
                    for (org.apache.poi.ss.usermodel.Cell cell : row) {
                        String cellValue = getCellText(cell);
                        if (cellValue != null && !cellValue.trim().isEmpty()) {
                            cellTexts.add(cellValue.trim());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("提取Excel文本失败: {}", excelFile.getName(), e);
        }
        return cellTexts;
    }

    private String getCellText(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) {
            return null;
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default:
                return null;
        }
    }

    /**
     * 审查 PDF 文档
     */
    private void reviewPdfDocument(File file, ValidationResult result) {
        List<String> paragraphs = PDFParser.extractText(file);

        CompletableFuture<List<ReviewIssue>> securityFuture = CompletableFuture.supplyAsync(
                () -> textSecurityService.checkSensitive(paragraphs), reviewExecutor);

        CompletableFuture<List<ReviewIssue>> grammarFuture = CompletableFuture.supplyAsync(
                () -> checkAllParagraphsGrammar(paragraphs), reviewExecutor);

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(securityFuture, grammarFuture);
        try {
            allFutures.get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("PDF审查超时或异常", e);
        }

        addFutureResults(result, securityFuture, "文本安全审查");
        addFutureResults(result, grammarFuture, "语法审查");
    }

    /**
     * 审查图片文件
     */
    private void reviewImageFile(File file, ValidationResult result) {
        List<ReviewIssue> issues = imageComplianceService.checkImage(file);
        result.addIssues(issues);
    }

    /**
     * 检查所有段落的语法
     */
    private List<ReviewIssue> checkAllParagraphsGrammar(List<String> paragraphs) {
        List<ReviewIssue> allIssues = new ArrayList<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            List<ReviewIssue> issues = grammarService.checkGrammar(paragraphs.get(i));
            for (ReviewIssue issue : issues) {
                issue.setLocation("段落" + (i + 1) + " " + issue.getLocation());
            }
            allIssues.addAll(issues);
        }
        return allIssues;
    }

    /**
     * 安全地添加 Future 结果
     */
    private void addFutureResults(ValidationResult result, CompletableFuture<List<ReviewIssue>> future, String taskName) {
        try {
            if (future.isDone()) {
                List<ReviewIssue> issues = future.get();
                result.addIssues(issues);
                log.debug("{} 完成，发现问题: {} 个", taskName, issues.size());
            } else {
                log.warn("{} 未完成", taskName);
            }
        } catch (Exception e) {
            log.error("{} 异常", taskName, e);
        }
    }

    /**
     * 快速文本审查接口（仅敏感词检查）
     */
    @PostMapping("/quick-check")
    public ResponseEntity<ValidationResponse> quickCheck(@RequestParam("text") String text) {
        long startTime = System.currentTimeMillis();
        String documentId = UUID.randomUUID().toString().substring(0, 8);

        List<ReviewIssue> issues = textSecurityService.checkSensitive(text);

        ValidationResponse response = ValidationResponse.builder()
                .documentId(documentId)
                .fileName("文本快速审查")
                .compliant(issues.isEmpty())
                .totalIssues(issues.size())
                .issues(issues)
                .reviewTime(LocalDateTime.now())
                .durationMs(System.currentTimeMillis() - startTime)
                .summary(issues.isEmpty() ? "未发现敏感词" : "发现 " + issues.size() + " 个敏感词")
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * 词库重新加载接口
     */
    @PostMapping("/reload-words")
    public ResponseEntity<Map<String, Object>> reloadWordLibrary() {
        textSecurityService.reloadWordLibraries();
        Map<String, Object> result = new HashMap<>();
        result.put("success", textSecurityService.isWordLibraryLoaded());
        result.put("message", "词库已重新加载");
        return ResponseEntity.ok(result);
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("wordLibraryLoaded", textSecurityService.isWordLibraryLoaded());
        status.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(status);
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }

    private void buildResponseMetrics(ValidationResponse response, List<ReviewIssue> issues) {
        int errors = 0, warnings = 0, infos = 0;
        for (ReviewIssue issue : issues) {
            switch (issue.getSeverity()) {
                case ERROR: errors++; break;
                case WARNING: warnings++; break;
                case INFO: infos++; break;
            }
        }
        response.setErrorCount(errors);
        response.setWarningCount(warnings);
        response.setInfoCount(infos);
    }

    private ValidationResponse buildResponse(ValidationResult result, long durationMs) {
        ValidationResponse response = ValidationResponse.builder()
                .documentId(result.getDocumentId())
                .fileName(result.getFileName())
                .compliant(result.isCompliant())
                .totalIssues(result.getRealIssueCount())
                .issues(result.getIssues())
                .reviewTime(result.getReviewTime())
                .durationMs(durationMs)
                .build();

        buildResponseMetrics(response, result.getIssues());

        if (response.getErrorCount() == 0 && response.getWarningCount() == 0 && response.getInfoCount() == 0) {
            response.setSummary("文档审查通过，未发现合规问题");
        } else {
            response.setSummary(String.format("发现 %d 个问题（错误: %d, 警告: %d, 提示: %d），审查耗时: %dms",
                    result.getRealIssueCount(), response.getErrorCount(),
                    response.getWarningCount(), response.getInfoCount(), durationMs));
        }

        return response;
    }
}