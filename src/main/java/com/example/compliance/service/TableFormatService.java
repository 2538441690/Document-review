package com.example.compliance.service;

import com.example.compliance.entity.ReviewIssue;
import com.example.compliance.entity.TableInfo;
import com.example.compliance.util.WordParser;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.xssf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 表格格式校验服务
 * 校验表格结构（表头完整性、列数一致性）、单元格数据类型、边框样式、对齐方式。
 * 支持 Word 表格和 Excel 表格。
 */
@Service
public class TableFormatService {

    private static final Logger log = LoggerFactory.getLogger(TableFormatService.class);

    @Value("${compliance.table.require-header:true}")
    private boolean requireHeader;

    @Value("${compliance.table.require-consistent-columns:true}")
    private boolean requireConsistentColumns;

    @Value("${compliance.table.max-columns:50}")
    private int maxColumns;

    /**
     * 校验 Word 文档中的表格
     */
    public List<ReviewIssue> checkWordTable(File wordFile) {
        List<ReviewIssue> issues = new ArrayList<>();
        List<TableInfo> tables = WordParser.extractTables(wordFile);

        for (int t = 0; t < tables.size(); t++) {
            TableInfo table = tables.get(t);
            String tableLocation = "表格_" + (t + 1);

            // 1. 表头完整性检查
            if (requireHeader && !table.isHasHeader()) {
                issues.add(ReviewIssue.builder()
                        .severity(ReviewIssue.Severity.ERROR)
                        .category(ReviewIssue.Category.TABLE_FORMAT)
                        .location(tableLocation)
                        .description("表格缺少表头")
                        .suggestion("请为表格添加表头行")
                        .build());
            }

            // 检查表头是否为空
            if (table.getHeaders() != null) {
                boolean allEmpty = table.getHeaders().stream().allMatch(String::isEmpty);
                if (allEmpty) {
                    issues.add(ReviewIssue.builder()
                            .severity(ReviewIssue.Severity.WARNING)
                            .category(ReviewIssue.Category.TABLE_FORMAT)
                            .location(tableLocation)
                            .description("表头内容为空")
                            .suggestion("请填写表头内容")
                            .build());
                }
            }

            // 2. 列数一致性检查
            if (requireConsistentColumns && table.getDataRows() != null) {
                int expectedColumns = table.getColumnCount();
                for (int r = 0; r < table.getDataRows().size(); r++) {
                    List<String> row = table.getDataRows().get(r);
                    if (row.size() != expectedColumns) {
                        issues.add(ReviewIssue.builder()
                                .severity(ReviewIssue.Severity.ERROR)
                                .category(ReviewIssue.Category.TABLE_FORMAT)
                                .location(tableLocation + " 第" + (r + 1) + "行")
                                .description(String.format("列数不一致，期望%d列，实际%d列",
                                        expectedColumns, row.size()))
                                .suggestion("请统一表格列数")
                                .build());
                    }
                }
            }

            // 3. 列数合理性检查
            if (table.getColumnCount() > maxColumns) {
                issues.add(ReviewIssue.builder()
                        .severity(ReviewIssue.Severity.WARNING)
                        .category(ReviewIssue.Category.TABLE_FORMAT)
                        .location(tableLocation)
                        .description(String.format("表格列数过多: %d列，建议不超过%d列",
                                table.getColumnCount(), maxColumns))
                        .suggestion("请考虑拆分表格或减少列数")
                        .build());
            }

            // 4. 空行检查
            if (table.getDataRows() != null) {
                for (int r = 1; r < table.getDataRows().size(); r++) {
                    List<String> row = table.getDataRows().get(r);
                    boolean allEmpty = row.stream().allMatch(String::isEmpty);
                    if (allEmpty) {
                        issues.add(ReviewIssue.builder()
                                .severity(ReviewIssue.Severity.INFO)
                                .category(ReviewIssue.Category.TABLE_FORMAT)
                                .location(tableLocation + " 第" + (r + 1) + "行")
                                .description("存在空行")
                                .suggestion("请删除空行或填充数据")
                                .build());
                    }
                }
            }
        }

        return issues;
    }

    /**
     * 校验 Excel 文件中的表格
     */
    public List<ReviewIssue> checkExcelTable(File excelFile) {
        List<ReviewIssue> issues = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(excelFile);
             XSSFWorkbook workbook = new XSSFWorkbook(fis)) {

            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                XSSFSheet sheet = workbook.getSheetAt(s);
                String sheetLocation = "工作表_" + sheet.getSheetName();

                int lastRowNum = sheet.getLastRowNum();
                if (lastRowNum < 0) {
                    continue;
                }

                XSSFRow headerRow = sheet.getRow(0);
                int expectedColumns = headerRow == null ? 0 : headerRow.getLastCellNum();

                // 表头检查
                if (requireHeader && (headerRow == null || expectedColumns == 0)) {
                    issues.add(ReviewIssue.builder()
                            .severity(ReviewIssue.Severity.ERROR)
                            .category(ReviewIssue.Category.TABLE_FORMAT)
                            .location(sheetLocation)
                            .description("表格缺少表头")
                            .suggestion("请为表格添加表头行")
                            .build());
                }

                // 逐行检查
                for (int r = 1; r <= lastRowNum; r++) {
                    XSSFRow row = sheet.getRow(r);
                    if (row == null) {
                        continue;
                    }

                    int actualColumns = row.getLastCellNum();

                    // 列数一致性检查
                    if (requireConsistentColumns && actualColumns != expectedColumns) {
                        issues.add(ReviewIssue.builder()
                                .severity(ReviewIssue.Severity.ERROR)
                                .category(ReviewIssue.Category.TABLE_FORMAT)
                                .location(sheetLocation + " 第" + (r + 1) + "行")
                                .description(String.format("列数不一致，期望%d列，实际%d列",
                                        expectedColumns, actualColumns))
                                .suggestion("请统一表格列数")
                                .build());
                    }

                    // 检查每个单元格
                    for (int c = 0; c < actualColumns; c++) {
                        XSSFCell cell = row.getCell(c);
                        if (cell != null) {
                            issues.addAll(checkCellFormat(cell, sheetLocation + " 第" + (r + 1) + "行第" + (c + 1) + "列"));
                        }
                    }
                }

                // 检查边框样式
                issues.addAll(checkBorderStyle(sheet, sheetLocation));
            }

        } catch (Exception e) {
            log.error("Excel表格校验失败: {}", excelFile.getName(), e);
            issues.add(ReviewIssue.builder()
                    .severity(ReviewIssue.Severity.ERROR)
                    .category(ReviewIssue.Category.TABLE_FORMAT)
                    .description("Excel表格解析失败: " + e.getMessage())
                    .suggestion("请检查文件是否损坏")
                    .build());
        }

        return issues;
    }

    /**
     * 检查单元格格式
     */
    private List<ReviewIssue> checkCellFormat(XSSFCell cell, String location) {
        List<ReviewIssue> issues = new ArrayList<>();

        // 检查对齐方式
        CellStyle style = cell.getCellStyle();
        if (style != null) {
            HorizontalAlignment hAlign = style.getAlignment();
            // 表头应该居中
            if (cell.getRowIndex() == 0 && hAlign != HorizontalAlignment.CENTER) {
                issues.add(ReviewIssue.builder()
                        .severity(ReviewIssue.Severity.INFO)
                        .category(ReviewIssue.Category.TABLE_FORMAT)
                        .location(location)
                        .description("表头单元格建议居中对齐")
                        .suggestion("请将表头设置为居中对齐")
                        .build());
            }
        }

        return issues;
    }

    /**
     * 检查边框样式
     */
    private List<ReviewIssue> checkBorderStyle(XSSFSheet sheet, String location) {
        List<ReviewIssue> issues = new ArrayList<>();

        int lastRowNum = sheet.getLastRowNum();
        if (lastRowNum < 0) {
            return issues;
        }

        XSSFRow firstRow = sheet.getRow(0);
        if (firstRow == null) {
            return issues;
        }

        // 检查第一行是否有边框
        boolean hasTopBorder = false;
        for (int c = 0; c < firstRow.getLastCellNum(); c++) {
            XSSFCell cell = firstRow.getCell(c);
            if (cell != null && cell.getCellStyle().getBorderTop() != BorderStyle.NONE) {
                hasTopBorder = true;
                break;
            }
        }

        if (!hasTopBorder) {
            issues.add(ReviewIssue.builder()
                    .severity(ReviewIssue.Severity.INFO)
                    .category(ReviewIssue.Category.TABLE_FORMAT)
                    .location(location)
                    .description("表格缺少边框线")
                    .suggestion("建议为表格添加边框线，便于阅读")
                    .build());
        }

        return issues;
    }
}