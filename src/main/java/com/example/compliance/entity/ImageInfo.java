package com.example.compliance.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageInfo {

    private String imageName;

    private String format;

    private int width;

    private int height;

    private int dpi;

    private long fileSize;

    private String ocrText;
}