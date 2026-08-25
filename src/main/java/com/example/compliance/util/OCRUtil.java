package com.example.compliance.util;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Tesseract OCR 封装工具
 * 使用 Tess4J 调用 Tesseract 引擎，提取图片中的文字
 */
public class OCRUtil {

    private static final Logger log = LoggerFactory.getLogger(OCRUtil.class);

    private final Tesseract tesseract;

    public OCRUtil(String tessDataPath, String language) {
        this.tesseract = new Tesseract();
        this.tesseract.setDatapath(tessDataPath);
        this.tesseract.setLanguage(language);
        // 设置 OCR 引擎模式：LSTM_ONLY
        this.tesseract.setOcrEngineMode(1);
        // 设置页面分割模式：自动检测
        this.tesseract.setPageSegMode(3);
    }

    /**
     * 从图片文件提取文字
     *
     * @param imageFile 图片文件
     * @return 提取的文字，失败返回空字符串
     */
    public String extractText(File imageFile) {
        try {
            return tesseract.doOCR(imageFile);
        } catch (TesseractException e) {
            log.error("OCR提取文字失败: {}", imageFile.getName(), e);
            return "";
        }
    }
}