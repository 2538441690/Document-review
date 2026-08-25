package com.example.compliance;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;

/**
 * 测试数据生成器
 * 一键生成覆盖各种场景的测试文件
 * 运行方式：
 *   .\mvnw.cmd exec:java "-Dexec.mainClass=com.example.compliance.TestDataGenerator"
 */
public class TestDataGenerator {

    private static final Path OUTPUT_DIR = Paths.get("test-files");

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT_DIR);
        System.out.println("=== 开始生成测试文件到 " + OUTPUT_DIR.toAbsolutePath() + " ===\n");

        generateDocxWithSensitiveWords();
        generateDocxWithGrammarIssues();
        generateDocxClean();
        generateDocxMixedFormat();
        generateXlsxWithTableIssues();
        generateXlsxClean();
        generateSimpleImage();
        generateSimpleTextImage();

        System.out.println("\n=== 全部生成完成！共 8 个测试文件 ===");
        System.out.println("测试命令：");
        System.out.println("  curl -X POST http://localhost:8080/api/document/review -F \"file=@test-files\\敏感词测试.docx\"");
        System.out.println("  curl -X POST http://localhost:8080/api/document/review -F \"file=@test-files\\语法错误测试.docx\"");
        System.out.println("  curl -X POST http://localhost:8080/api/document/review -F \"file=@test-files\\干净文档.docx\"");
        System.out.println("  curl -X POST http://localhost:8080/api/document/review -F \"file=@test-files\\格式混乱.docx\"");
        System.out.println("  curl -X POST http://localhost:8080/api/document/review -F \"file=@test-files\\表格问题.xlsx\"");
        System.out.println("  curl -X POST http://localhost:8080/api/document/review -F \"file=@test-files\\干净表格.xlsx\"");
        System.out.println("  curl -X POST http://localhost:8080/api/document/review -F \"file=@test-files\\测试图片.png\"");
        System.out.println("  curl -X POST http://localhost:8080/api/document/quick-check -d \"text=这份文件包含机密和绝密信息\"");
    }

    // ==================== 1. 含敏感词的 Word 文档 ====================
    static void generateDocxWithSensitiveWords() throws Exception {
        Path path = OUTPUT_DIR.resolve("敏感词测试.docx");
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph p1 = doc.createParagraph();
            XWPFRun run1 = p1.createRun();
            run1.setText("关于机密项目的内部资料说明");
            run1.setFontSize(14);
            run1.setFontFamily("宋体");

            XWPFParagraph p2 = doc.createParagraph();
            XWPFRun run2 = p2.createRun();
            run2.setText("本文件涉及绝密信息和涉密词汇示例，请严格保密。");
            run2.setFontSize(12);

            XWPFParagraph p3 = doc.createParagraph();
            XWPFRun run3 = p3.createRun();
            run3.setText("这是一个敏感词示例1的测试段落，包含敏感词示例2。");
            run3.setFontSize(12);

            try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
                doc.write(fos);
            }
        }
        System.out.println("[OK] 敏感词测试.docx - 包含 机密、绝密、内部资料、涉密词汇示例、敏感词示例1、敏感词示例2");
    }

    // ==================== 2. 含语法错误的 Word 文档 ====================
    static void generateDocxWithGrammarIssues() throws Exception {
        Path path = OUTPUT_DIR.resolve("语法错误测试.docx");
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph p1 = doc.createParagraph();
            XWPFRun run1 = p1.createRun();
            run1.setText("这个问题我们需要认真对待，我们必须处理这件事情。");
            run1.setFontSize(12);

            XWPFParagraph p2 = doc.createParagraph();
            XWPFRun run2 = p2.createRun();
            run2.setText("这是一个很好很好的系统，非常非常实用。");
            run2.setFontSize(12);

            XWPFParagraph p3 = doc.createParagraph();
            XWPFRun run3 = p3.createRun();
            run3.setText("根据要求，我们进行了深入的深入的分析。");
            run3.setFontSize(12);

            try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
                doc.write(fos);
            }
        }
        System.out.println("[OK] 语法错误测试.docx - 包含重复词、啰嗦表达");
    }

    // ==================== 3. 干净文档（无问题） ====================
    static void generateDocxClean() throws Exception {
        Path path = OUTPUT_DIR.resolve("干净文档.docx");
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph p1 = doc.createParagraph();
            XWPFRun run1 = p1.createRun();
            run1.setText("项目进度报告");
            run1.setFontSize(16);
            run1.setFontFamily("宋体");
            run1.setBold(true);

            XWPFParagraph p2 = doc.createParagraph();
            XWPFRun run2 = p2.createRun();
            run2.setText("本周完成了系统模块的集成测试工作，各项指标均达到预期标准。");
            run2.setFontSize(12);

            XWPFParagraph p3 = doc.createParagraph();
            XWPFRun run3 = p3.createRun();
            run3.setText("下一步计划开展用户验收测试，预计下周完成。");
            run3.setFontSize(12);

            // 添加一个标准表格
            XWPFTable table = doc.createTable(3, 3);
            table.getRow(0).getCell(0).setText("序号");
            table.getRow(0).getCell(1).setText("任务名称");
            table.getRow(0).getCell(2).setText("状态");
            table.getRow(1).getCell(0).setText("1");
            table.getRow(1).getCell(1).setText("集成测试");
            table.getRow(1).getCell(2).setText("已完成");
            table.getRow(2).getCell(0).setText("2");
            table.getRow(2).getCell(1).setText("验收测试");
            table.getRow(2).getCell(2).setText("进行中");

            try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
                doc.write(fos);
            }
        }
        System.out.println("[OK] 干净文档.docx - 无敏感词、无语法问题，含标准表格");
    }

    // ==================== 4. 格式混乱的 Word 文档 ====================
    static void generateDocxMixedFormat() throws Exception {
        Path path = OUTPUT_DIR.resolve("格式混乱.docx");
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph p1 = doc.createParagraph();
            XWPFRun run1 = p1.createRun();
            run1.setText("标题使用非标准字体");
            run1.setFontFamily("Arial");
            run1.setFontSize(10);

            XWPFParagraph p2 = doc.createParagraph();
            XWPFRun run2 = p2.createRun();
            run2.setText("正文段落使用过小字号");
            run2.setFontSize(8);

            XWPFParagraph p3 = doc.createParagraph();
            XWPFRun run3 = p3.createRun();
            run3.setText("另一个段落使用过大字号");
            run3.setFontSize(20);

            // 混合格式的段落
            XWPFParagraph p4 = doc.createParagraph();
            XWPFRun run4a = p4.createRun();
            run4a.setText("前半段用宋体");
            run4a.setFontFamily("宋体");
            run4a.setFontSize(12);
            XWPFRun run4b = p4.createRun();
            run4b.setText("后半段用英文");
            run4b.setFontFamily("Times New Roman");
            run4b.setFontSize(12);

            // 表格：缺少表头
            XWPFTable table = doc.createTable(2, 2);
            table.getRow(0).getCell(0).setText("数据1");
            table.getRow(0).getCell(1).setText("数据2");
            table.getRow(1).getCell(0).setText("数据3");
            table.getRow(1).getCell(1).setText("数据4");

            try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
                doc.write(fos);
            }
        }
        System.out.println("[OK] 格式混乱.docx - 字体不统一、字号不规范、表格无表头");
    }

    // ==================== 5. 有问题的 Excel 表格 ====================
    static void generateXlsxWithTableIssues() throws Exception {
        Path path = OUTPUT_DIR.resolve("表格问题.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("测试数据");

            // 无表头，直接放数据
            Row row0 = sheet.createRow(0);
            row0.createCell(0).setCellValue("机密数据");
            row0.createCell(1).setCellValue("12345");
            row0.createCell(2).setCellValue("2024-01-01");

            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("绝密信息");
            row1.createCell(1).setCellValue("67890");
            // 故意少一列，列数不一致

            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("内部资料");
            row2.createCell(1).setCellValue("敏感词示例1");

            try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
                workbook.write(fos);
            }
        }
        System.out.println("[OK] 表格问题.xlsx - 无表头、列数不一致、含敏感词");
    }

    // ==================== 6. 干净的 Excel 表格 ====================
    static void generateXlsxClean() throws Exception {
        Path path = OUTPUT_DIR.resolve("干净表格.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("项目统计");

            // 标准表头
            Row header = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            Cell cell0 = header.createCell(0);
            cell0.setCellValue("序号");
            cell0.setCellStyle(headerStyle);
            Cell cell1 = header.createCell(1);
            cell1.setCellValue("项目名称");
            cell1.setCellStyle(headerStyle);
            Cell cell2 = header.createCell(2);
            cell2.setCellValue("完成进度");
            cell2.setCellStyle(headerStyle);

            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("1");
            row1.createCell(1).setCellValue("系统集成");
            row1.createCell(2).setCellValue("100%");

            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("2");
            row2.createCell(1).setCellValue("用户培训");
            row2.createCell(2).setCellValue("80%");

            Row row3 = sheet.createRow(3);
            row3.createCell(0).setCellValue("3");
            row3.createCell(1).setCellValue("文档编写");
            row3.createCell(2).setCellValue("60%");

            try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
                workbook.write(fos);
            }
        }
        System.out.println("[OK] 干净表格.xlsx - 标准表头、列数一致、无敏感词");
    }

    // ==================== 7. 简单图片 ====================
    static void generateSimpleImage() throws Exception {
        Path path = OUTPUT_DIR.resolve("测试图片.png");
        BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2d = image.createGraphics();
        g2d.setColor(java.awt.Color.WHITE);
        g2d.fillRect(0, 0, 200, 100);
        g2d.setColor(java.awt.Color.BLACK);
        g2d.setFont(new java.awt.Font("宋体", java.awt.Font.PLAIN, 16));
        g2d.drawString("测试图片", 60, 50);
        g2d.dispose();
        ImageIO.write(image, "png", path.toFile());
        System.out.println("[OK] 测试图片.png - 200x100 简单PNG图片");
    }

    // ==================== 8. 含文字图片（OCR 测试） ====================
    static void generateSimpleTextImage() throws Exception {
        Path path = OUTPUT_DIR.resolve("敏感文字图片.png");
        BufferedImage image = new BufferedImage(400, 150, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2d = image.createGraphics();
        g2d.setColor(java.awt.Color.WHITE);
        g2d.fillRect(0, 0, 400, 150);
        g2d.setColor(java.awt.Color.BLACK);
        g2d.setFont(new java.awt.Font("宋体", java.awt.Font.PLAIN, 20));
        g2d.drawString("机密文件", 20, 40);
        g2d.drawString("内部资料", 250, 40);
        g2d.drawString("请勿外传", 20, 80);
        g2d.drawString("绝密", 250, 80);
        g2d.dispose();
        ImageIO.write(image, "png", path.toFile());
        System.out.println("[OK] 敏感文字图片.png - 含机密、绝密、内部资料文字（OCR检测用）");
    }
}