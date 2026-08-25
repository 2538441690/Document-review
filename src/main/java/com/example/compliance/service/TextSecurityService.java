package com.example.compliance.service;

import com.example.compliance.entity.ReviewIssue;
import com.example.compliance.util.DFAFilter;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 文本安全性校验服务
 * 基于 DFA 算法检测文档中的敏感词、涉密词。
 * 支持词库热加载：通过 WatchService 监听词库文件变更，自动重建 DFA 状态机。
 * 使用 Caffeine 缓存最近审查结果，提高重复文档审查效率。
 */
@Service
public class TextSecurityService {

    private static final Logger log = LoggerFactory.getLogger(TextSecurityService.class);

    @Getter
    private final DFAFilter dfaFilter = new DFAFilter();

    private final Cache<String, List<ReviewIssue>> reviewCache;

    @Value("${compliance.sensitive-word-dir:config/sensitive-words}")
    private String sensitiveWordDir;

    @Getter
    private volatile boolean wordLibraryLoaded = false;

    public TextSecurityService() {
        this.reviewCache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .build();
    }

    @PostConstruct
    public void init() {
        // Caffeine 缓存初始化后重新设置
        // 实际参数在 application.yml 配置后生效
        loadWordLibraries();
        startWordLibraryWatcher();
    }

    /**
     * 检查文本中的敏感词
     *
     * @param text 待检查文本
     * @return 敏感词问题列表
     */
    public List<ReviewIssue> checkSensitive(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

        // 先从缓存中查找
        String cacheKey = generateCacheKey(text);
        List<ReviewIssue> cached = reviewCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<ReviewIssue> issues = new ArrayList<>();
        List<DFAFilter.MatchResult> matches = dfaFilter.scan(text);

        for (DFAFilter.MatchResult match : matches) {
            ReviewIssue issue = ReviewIssue.builder()
                    .severity(ReviewIssue.Severity.ERROR)
                    .category(ReviewIssue.Category.SENSITIVE_WORD)
                    .location("位置: " + match.startIndex() + "-" + match.endIndex())
                    .description("检测到" + match.category() + ": " + match.word())
                    .suggestion("请删除或替换该敏感词")
                    .build();
            issues.add(issue);
        }

        // 写入缓存
        reviewCache.put(cacheKey, issues);

        return issues;
    }

    /**
     * 批量检查多个文本段落
     */
    public List<ReviewIssue> checkSensitive(List<String> paragraphs) {
        List<ReviewIssue> allIssues = new ArrayList<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            String paragraph = paragraphs.get(i);
            List<ReviewIssue> issues = checkSensitive(paragraph);
            for (ReviewIssue issue : issues) {
                issue.setLocation("段落" + (i + 1) + " " + issue.getLocation());
            }
            allIssues.addAll(issues);
        }
        return allIssues;
    }

    /**
     * 手动重新加载词库
     */
    public void reloadWordLibraries() {
        loadWordLibraries();
    }

    /**
     * 加载所有词库文件
     */
    private void loadWordLibraries() {
        try {
            Path dir = Paths.get(sensitiveWordDir);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                log.warn("敏感词库目录不存在，已创建: {}", dir.toAbsolutePath());
                // 创建示例词库文件
                createSampleWordLibrary(dir);
            }

            Map<String, List<String>> categoryWordsMap = new LinkedHashMap<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.txt")) {
                for (Path path : stream) {
                    String category = path.getFileName().toString().replace(".txt", "");
                    List<String> words = Files.readAllLines(path);
                    words.removeIf(w -> w.trim().isEmpty() || w.startsWith("#"));
                    if (!words.isEmpty()) {
                        categoryWordsMap.put(category, words);
                        log.info("加载词库: {}，词条数: {}", category, words.size());
                    }
                }
            }

            if (!categoryWordsMap.isEmpty()) {
                dfaFilter.rebuildLibrary(categoryWordsMap);
                // 设置忽略的干扰字符
                Set<Character> ignoreChars = new HashSet<>();
                char[] chars = {' ', '\t', '\n', '\r', '，', '。', '！', '？', '、', '：', '；',
                        ',', '.', '!', '?', ':', ';', '"', '\'', '（', '）', '(', ')', '【', '】'};
                for (char c : chars) {
                    ignoreChars.add(c);
                }
                dfaFilter.setIgnoreChars(ignoreChars);
                wordLibraryLoaded = true;
            }
        } catch (IOException e) {
            log.error("加载词库失败", e);
        }
    }

    /**
     * 创建示例词库文件
     */
    private void createSampleWordLibrary(Path dir) throws IOException {
        List<String> sensitiveWords = Arrays.asList(
                "# 敏感词库示例",
                "敏感词示例1",
                "敏感词示例2",
                "涉密词汇示例"
        );
        Files.write(dir.resolve("敏感词.txt"), sensitiveWords);

        List<String> secretWords = Arrays.asList(
                "# 涉密词库示例",
                "机密",
                "绝密",
                "秘密",
                "内部资料"
        );
        Files.write(dir.resolve("涉密词.txt"), secretWords);
    }

    /**
     * 启动词库文件监听器，实现热加载
     */
    private void startWordLibraryWatcher() {
        Thread watcherThread = new Thread(() -> {
            try {
                Path dir = Paths.get(sensitiveWordDir);
                if (!Files.exists(dir)) {
                    Files.createDirectories(dir);
                }
                WatchService watchService = FileSystems.getDefault().newWatchService();
                dir.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);

                log.info("词库文件监听器已启动，监听目录: {}", dir.toAbsolutePath());

                while (true) {
                    WatchKey key = watchService.take();
                    boolean needsReload = false;
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() != StandardWatchEventKinds.OVERFLOW) {
                            Path changed = (Path) event.context();
                            if (changed.toString().endsWith(".txt")) {
                                needsReload = true;
                                log.info("词库文件变更: {}，即将重新加载", changed);
                            }
                        }
                    }
                    if (needsReload) {
                        delay();
                        loadWordLibraries();
                        reviewCache.invalidateAll();
                    }
                    if (!key.reset()) {
                        break;
                    }
                }
            } catch (IOException | InterruptedException e) {
                log.error("词库监听器异常", e);
            }
        }, "word-library-watcher");
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    private String generateCacheKey(String text) {
        return Integer.toHexString(text.hashCode());
    }

    private static void delay() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}