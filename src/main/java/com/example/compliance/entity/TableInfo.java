package com.example.compliance.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableInfo {

    private String tableName;

    private int rowCount;

    private int columnCount;

    private boolean hasHeader;

    private List<String> headers;

    private List<List<String>> dataRows;

    private String borderStyle;

    private String alignment;
}