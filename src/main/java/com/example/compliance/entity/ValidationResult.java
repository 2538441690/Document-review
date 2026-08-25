package com.example.compliance.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {

    private String documentId;

    private String fileName;

    private boolean compliant;

    @Builder.Default
    private List<ReviewIssue> issues = new ArrayList<>();

    private LocalDateTime reviewTime;

    private long durationMs;

    public void addIssue(ReviewIssue issue) {
        this.issues.add(issue);
        if (issue.getSeverity() == ReviewIssue.Severity.ERROR
                || issue.getSeverity() == ReviewIssue.Severity.WARNING) {
            this.compliant = false;
        }
    }

    public void addIssues(List<ReviewIssue> newIssues) {
        this.issues.addAll(newIssues);
        for (ReviewIssue issue : newIssues) {
            if (issue.getSeverity() == ReviewIssue.Severity.ERROR
                    || issue.getSeverity() == ReviewIssue.Severity.WARNING) {
                this.compliant = false;
                break;
            }
        }
    }

    /**
     * 获取真正的问题数（仅 ERROR 和 WARNING，不含 INFO）
     */
    public int getRealIssueCount() {
        return (int) issues.stream()
                .filter(i -> i.getSeverity() == ReviewIssue.Severity.ERROR
                        || i.getSeverity() == ReviewIssue.Severity.WARNING)
                .count();
    }
}