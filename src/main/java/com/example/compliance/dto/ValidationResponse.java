package com.example.compliance.dto;

import com.example.compliance.entity.ReviewIssue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResponse {

    private String documentId;

    private String fileName;

    private boolean compliant;

    private int totalIssues;

    private int errorCount;

    private int warningCount;

    private int infoCount;

    private List<ReviewIssue> issues;

    private LocalDateTime reviewTime;

    private long durationMs;

    private String summary;
}