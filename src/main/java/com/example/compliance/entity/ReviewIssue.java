package com.example.compliance.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewIssue {

    public enum Severity {
        ERROR,
        WARNING,
        INFO
    }

    public enum Category {
        SENSITIVE_WORD,
        GRAMMAR,
        TEXT_FORMAT,
        IMAGE_FORMAT,
        IMAGE_SENSITIVE,
        TABLE_FORMAT
    }

    private Severity severity;

    private Category category;

    private String location;

    private String description;

    private String suggestion;
}