package com.example.compliance.util;

import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.tiff.TiffField;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * 图片元数据解析工具
 * 基于 Apache Commons Imaging 读取图片格式、尺寸、DPI 等元数据
 */
public class ImageParser {

    private static final Logger log = LoggerFactory.getLogger(ImageParser.class);

    /**
     * 图片基础信息
     */
    public static class ImageMetaInfo {
        public String format;
        public int width;
        public int height;
        public int dpi;
        public long fileSize;

        @Override
        public String toString() {
            return String.format("ImageMeta{format=%s, size=%dx%d, dpi=%d, fileSize=%d}",
                    format, width, height, dpi, fileSize);
        }
    }

    /**
     * 读取图片元数据信息
     */
    public static ImageMetaInfo readImageMeta(File imageFile) {
        ImageMetaInfo info = new ImageMetaInfo();
        info.fileSize = imageFile.length();

        try {
            // 获取格式
            info.format = Imaging.guessFormat(imageFile).getName();

            // 获取尺寸
            BufferedImage image = ImageIO.read(imageFile);
            if (image != null) {
                info.width = image.getWidth();
                info.height = image.getHeight();
            }

            // 获取 DPI
            info.dpi = readDpi(imageFile);

        } catch (Exception e) {
            log.error("读取图片元数据失败: {}", imageFile.getName(), e);
        }

        return info;
    }

    /**
     * 读取图片 DPI
     */
    private static int readDpi(File imageFile) {
        try {
            ImageMetadata metadata = Imaging.getMetadata(imageFile);
            if (metadata instanceof JpegImageMetadata jpegMetadata) {
                TiffField field = jpegMetadata.findEXIFValueWithExactMatch(
                        TiffTagConstants.TIFF_TAG_XRESOLUTION);
                if (field != null) {
                    return field.getIntValue();
                }
            }
        } catch (Exception e) {
            log.debug("读取DPI失败: {}", imageFile.getName());
        }

        // 默认尝试通过 ImageIO 读取
        try {
            BufferedImage image = ImageIO.read(imageFile);
            if (image != null) {
                // 默认屏幕 DPI 为 72，实际打印 DPI 需从元数据获取
                return 72;
            }
        } catch (IOException e) {
            log.debug("ImageIO读取失败: {}", imageFile.getName());
        }
        return 0;
    }

    /**
     * 校验图片格式是否合规
     *
     * @param format 实际格式
     * @param allowedFormats 允许的格式列表
     * @return 是否合规
     */
    public static boolean isValidFormat(String format, String[] allowedFormats) {
        for (String allowed : allowedFormats) {
            if (allowed.equalsIgnoreCase(format)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验图片分辨率是否合规
     *
     * @param width 实际宽度
     * @param height 实际高度
     * @param minWidth 最小宽度
     * @param minHeight 最小高度
     * @return 是否合规
     */
    public static boolean isValidResolution(int width, int height, int minWidth, int minHeight) {
        return width >= minWidth && height >= minHeight;
    }

    /**
     * 校验图片 DPI 是否合规
     */
    public static boolean isValidDpi(int dpi, int minDpi) {
        return dpi >= minDpi;
    }
}