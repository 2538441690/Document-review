package com.example.compliance.service;

import com.example.compliance.entity.ReviewIssue;
import org.languagetool.JLanguageTool;
import org.languagetool.Language;
import org.languagetool.Languages;
import org.languagetool.rules.RuleMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 语句规范校验服务
 * 集成 LanguageTool，检查语法错误、拼写错误、中英文标点混用。
 * 纯离线运行，不连接 LanguageTool 云端服务。
 */
@Service
public class GrammarService {

    private static final Logger log = LoggerFactory.getLogger(GrammarService.class);

    private JLanguageTool chineseTool;

    @PostConstruct
    public void init() {
        try {
            Language chinese = Languages.getLanguageForShortCode("zh-CN");
            chineseTool = new JLanguageTool(chinese);
            chineseTool.disableRule("WHITESPACE_RULE");
            chineseTool.disableRule("COMMA_PARENTHESIS_WHITESPACE");
            log.info("LanguageTool 中文引擎初始化完成");
        } catch (Exception e) {
            log.error("LanguageTool 初始化失败", e);
        }
    }

    /**
     * 检查文本的语法、拼写和标点问题
     *
     * @param text 待检查文本
     * @return 语法问题列表
     */
    public List<ReviewIssue> checkGrammar(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

        List<ReviewIssue> issues = new ArrayList<>();

        // LanguageTool 检查
        if (chineseTool != null) {
            try {
                List<RuleMatch> matches = chineseTool.check(text);
                for (RuleMatch match : matches) {
                    issues.add(ReviewIssue.builder()
                            .severity(mapSeverity(match))
                            .category(ReviewIssue.Category.GRAMMAR)
                            .location("位置: " + match.getFromPos() + "-" + match.getToPos())
                            .description(match.getMessage())
                            .suggestion(match.getSuggestedReplacements().isEmpty()
                                    ? "请检查语法"
                                    : "建议修改为: " + String.join(", ", match.getSuggestedReplacements()))
                            .build());
                }
            } catch (IOException e) {
                log.error("LanguageTool检查失败", e);
            }
        }

        // 中英文标点混用检查
        issues.addAll(checkPunctuationMixing(text));

        // 中文重复词检查
        issues.addAll(checkChineseRepeatedWords(text));

        return issues;
    }

    /**
     * 中文重复词检查
     * 检测连续重复的词语，如"很好很好"、"非常非常"、"深入的深入的"
     */
    private List<ReviewIssue> checkChineseRepeatedWords(String text) {
        List<ReviewIssue> issues = new ArrayList<>();
        int len = text.length();

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (isNonChinese(c)) {
                continue;
            }

            for (int wordLen = 1; wordLen <= 4; wordLen++) {
                int end = i + wordLen;
                if (end > len) {
                    break;
                }
                String word = text.substring(i, end);

                if (!isAllChinese(word)) {
                    continue;
                }

                int repeatEnd = end + wordLen;
                if (repeatEnd > len) {
                    continue;
                }
                String nextWord = text.substring(end, repeatEnd);

                if (word.equals(nextWord)) {
                    int totalEnd = findRepeatEnd(text, i, wordLen);
                    String fullMatch = text.substring(i, totalEnd);
                    issues.add(ReviewIssue.builder()
                            .severity(ReviewIssue.Severity.WARNING)
                            .category(ReviewIssue.Category.GRAMMAR)
                            .location("位置: " + i + "-" + totalEnd)
                            .description("重复词语: \"" + fullMatch + "\"")
                            .suggestion("请删除重复的词语，保留一处即可")
                            .build());
                    i = totalEnd - 1;
                    break;
                }
            }
        }

        return issues;
    }

    /**
     * 找出重复词的连续结束位置
     */
    private int findRepeatEnd(String text, int start, int wordLen) {
        int end = start + wordLen;
        while (end + wordLen <= text.length()) {
            String word = text.substring(start, start + wordLen);
            String nextWord = text.substring(end, end + wordLen);
            if (word.equals(nextWord)) {
                end += wordLen;
            } else {
                break;
            }
        }
        return end;
    }

    private boolean isNonChinese(char c) {
        return (c < 0x4E00 || c > 0x9FFF);
    }

    private boolean isAllChinese(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (isNonChinese(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 中英文标点混用检查
     */
    private List<ReviewIssue> checkPunctuationMixing(String text) {
        List<ReviewIssue> issues = new ArrayList<>();

        boolean hasChinese = false;
        boolean hasEnglish = false;

        for (char c : text.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FFF) {
                hasChinese = true;
            }
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                hasEnglish = true;
            }
        }

        if (hasChinese && hasEnglish) {
            // 检测中文文本后使用英文标点
            for (int i = 0; i < text.length() - 1; i++) {
                char c = text.charAt(i);
                if (isChinesePunctuation(c) && isEnglishContext(text, i)) {
                    issues.add(ReviewIssue.builder()
                            .severity(ReviewIssue.Severity.WARNING)
                            .category(ReviewIssue.Category.GRAMMAR)
                            .location("位置: " + i)
                            .description("中英文标点混用: '" + c + "'")
                            .suggestion("请统一使用中文标点")
                            .build());
                }
                if (isEnglishPunctuation(c) && isChineseContext(text, i)) {
                    issues.add(ReviewIssue.builder()
                            .severity(ReviewIssue.Severity.WARNING)
                            .category(ReviewIssue.Category.GRAMMAR)
                            .location("位置: " + i)
                            .description("中英文标点混用: '" + c + "'")
                            .suggestion("请统一使用中文标点")
                            .build());
                }
            }
        }

        return issues;
    }

    private boolean isChinesePunctuation(char c) {
        return c == '，' || c == '。' || c == '！' || c == '？' || c == '；' || c == '：' ||
                c == '、' || c == '（' || c == '）' || c == '“' || c == '”' || c == '‘' || c == '’';
    }

    private boolean isEnglishPunctuation(char c) {
        return c == ',' || c == '.' || c == '!' || c == '?' || c == ';' || c == ':' ||
                c == '(' || c == ')' || c == '"' || c == '\'';
    }

    private boolean isChineseContext(String text, int pos) {
        int start = Math.max(0, pos - 5);
        int end = Math.min(text.length(), pos + 5);
        String context = text.substring(start, end);
        for (char c : context.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FFF) return true;
        }
        return false;
    }

    private boolean isEnglishContext(String text, int pos) {
        int start = Math.max(0, pos - 5);
        int end = Math.min(text.length(), pos + 5);
        String context = text.substring(start, end);
        for (char c : context.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) return true;
        }
        return false;
    }

    private ReviewIssue.Severity mapSeverity(RuleMatch match) {
        // LanguageTool 没有直接的严重级别，根据规则类型映射
        String ruleId = match.getRule().getId().toUpperCase();
        if (ruleId.contains("SPELL") || ruleId.contains("GRAMMAR")) {
            return ReviewIssue.Severity.ERROR;
        } else if (ruleId.contains("STYLE") || ruleId.contains("TYPOGRAPHY")) {
            return ReviewIssue.Severity.WARNING;
        }
        return ReviewIssue.Severity.INFO;
    }
}