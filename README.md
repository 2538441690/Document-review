# Document-review

## 项目概述

面向企事业单位内部文档的智能化合规审查系统。支持 Word、Excel、PDF、图片等多种格式，覆盖敏感词检测、语法纠错、文本格式、表格规范、图片 OCR 五大审查维度。

**核心特点：所有算法和模型均在本地运行，不依赖任何外部 API 或网络服务，适用于涉密与内网环境。**

> [附图：系统主界面截图]

---

## 项目功能

### 审查能力矩阵

| 审查维度 | Word | Excel | PDF | 图片 | 技术实现 |
|:---:|:---:|:---:|:---:|:---:|:---|
| 敏感词检测 | ✅ | ✅ | ✅ | ✅ | DFA 有限状态机 |
| 语法检查 | ✅ | — | ✅ | — | LanguageTool |
| 文本格式 | ✅ | — | — | — | Apache POI |
| 表格格式 | ✅ | ✅ | — | — | Apache POI |
| 图片格式 | — | — | — | ✅ | ImageIO 元数据 |
| 图片 OCR | ✅ | — | ✅ | ✅ | Tesseract + Tess4J |

### 详细说明

#### 敏感词检测
- 基于 DFA（确定性有限自动机）算法，O(n) 时间复杂度，毫秒级扫描
- 支持多词库分类管理（涉密词、敏感词等），`config/sensitive-words/` 目录下 `.txt` 文件即时热加载
- 词库文件变更后自动重建索引，无需重启服务

#### 语法检查
- 集成 LanguageTool 6.3 中文引擎
- 检测中文语法错误、重复词语、中英文标点混用等问题
- 支持段落级批量审查

#### 文本格式校验
- 字体名称、字号范围、行距倍数的合规性校验
- 页边距（上下左右）精确到毫米级检查
- 所有规则参数可通过 `application.yml` 灵活配置

#### 表格格式校验
- 表头完整性检查（是否有表头行）
- 列数一致性检查（表格各行列数是否统一）
- 单表最大列数限制

#### 图片格式校验
- 图片格式白名单校验（PNG / JPEG / JPG / BMP）
- 分辨率下限检查（宽 × 高）
- DPI 下限检查
- 文件大小上限检查

#### 图片 OCR 敏感词检测
- 基于 Tesseract OCR 引擎提取图片中的文字
- 提取结果自动送入 DFA 敏感词引擎二次检测
- 支持中英文混合识别

> [附图：审查结果页面截图]

---

## 技术架构

| 层级 | 技术选型 |
|:---|:---|
| 后端框架 | Spring Boot 3.2.0 |
| 语言版本 | Java 17 |
| 文档解析 | Apache POI 5.2.5 / PDFBox 2.0.29 |
| OCR 引擎 | Tesseract 5.x（Tess4J 5.7.0） |
| 语法引擎 | LanguageTool 6.3 |
| 敏感词算法 | 自研 DFA 有限状态机 |
| 本地缓存 | Caffeine |
| 并发框架 | CompletableFuture + 自定义线程池 |
| 前端 | 原生 HTML5 + CSS3 + JavaScript |
| 构建工具 | Maven Wrapper（无需预装 Maven） |


---

## 使用方法

### 环境要求

- **JDK 17** 或更高版本
- 无需安装 Maven（项目内置 Maven Wrapper）
- 无需安装 Tesseract（OCR 语言包已内置在 `config/tessdata/`）

### 快速启动

```bash
# 1. 克隆项目
git clone https://github.com/your-username/your-repo.git
cd your-repo

# 2. 启动服务（首次运行会自动下载 Maven 及依赖）
mvnw.cmd spring-boot:run
```

> [附图：启动成功终端截图]

启动完成后访问 **http://localhost:8080** 即可使用。

### 配置说明

审查规则通过 `src/main/resources/application.yml` 配置，按需修改后重启生效：

```yaml
compliance:
  # 文本格式规范
  format:
    font-name: 宋体              # 字体要求
    font-size-min: 10.5          # 最小字号 (pt)
    font-size-max: 16.0          # 最大字号 (pt)
    line-spacing-min: 1.15       # 最小行距 (倍)
    line-spacing-max: 1.5        # 最大行距 (倍)
    margin-top-mm: 25.4          # 上边距 (mm)
    margin-bottom-mm: 25.4       # 下边距 (mm)
    margin-left-mm: 31.7         # 左边距 (mm)
    margin-right-mm: 31.7        # 右边距 (mm)
    margin-tolerance-mm: 2.0     # 边距容差 (mm)

  # 图片格式规范
  image:
    allowed-formats: PNG,JPEG,JPG,BMP
    min-width: 200
    min-height: 200
    min-dpi: 150
    max-size-mb: 10

  # 表格格式规范
  table:
    require-header: true
    require-consistent-columns: true
    max-columns: 50
```

### 自定义敏感词库

在 `config/sensitive-words/` 目录下创建或编辑 `.txt` 文件，**一行一个词**，系统会自动监听文件变化并实时生效：

```
config/sensitive-words/
├── 涉密词.txt
└── 敏感词.txt
```

> [附图：敏感词库文件编辑截图]

### 支持的文件格式

| 格式 | 扩展名 | 大小限制 |
|:---|:---|:---|
| Word | .docx | 50MB |
| Excel | .xlsx | 50MB |
| PDF | .pdf | 50MB |
| 图片 | .png .jpg .jpeg .bmp | 50MB |

---

## 项目结构

```
wzx/
├── pom.xml                              # Maven 项目配置
├── mvnw.cmd                             # Maven Wrapper (Windows)
├── .gitignore
├── config/
│   ├── sensitive-words/                 # 敏感词库（热加载）
│   │   ├── 涉密词.txt
│   │   └── 敏感词.txt
│   └── tessdata/                        # Tesseract OCR 语言包
│       ├── chi_sim.traineddata
│       └── eng.traineddata
├── src/main/java/com/example/compliance/
│   ├── ComplianceApplication.java       # 启动入口
│   ├── config/
│   │   └── ThreadPoolConfig.java        # 线程池配置
│   ├── controller/
│   │   └── DocumentController.java      # 审查接口控制器
│   ├── dto/
│   │   └── ValidationResponse.java      # 响应 DTO
│   ├── entity/
│   │   ├── ReviewIssue.java             # 合规问题实体
│   │   ├── ValidationResult.java        # 审查结果实体
│   │   ├── ImageInfo.java               # 图片信息实体
│   │   └── TableInfo.java               # 表格信息实体
│   ├── service/
│   │   ├── TextSecurityService.java     # 敏感词检测服务
│   │   ├── GrammarService.java          # 语法检查服务
│   │   ├── TextFormatService.java       # 文本格式校验服务
│   │   ├── TableFormatService.java      # 表格格式校验服务
│   │   └── ImageComplianceService.java  # 图片合规 + OCR 服务
│   └── util/
│       ├── DFAFilter.java               # DFA 敏感词过滤引擎
│       ├── WordParser.java              # Word 文档解析器
│       ├── PDFParser.java               # PDF 文档解析器
│       ├── ImageParser.java             # 图片元数据解析器
│       └── OCRUtil.java                 # Tesseract OCR 封装
└── src/main/resources/
    ├── application.yml                  # 应用配置
    └── static/
        ├── index.html                   # 前端主页面
        ├── css/style.css                # 样式
        └── js/app.js                    # 前端逻辑
```

---

## 常见问题

**Q: 启动时报 Tesseract OCR 初始化失败？**

A: 检查 `config/tessdata/` 目录下是否存在 `chi_sim.traineddata` 和 `eng.traineddata`。如缺失，请从 [tesseract-ocr/tessdata_fast](https://github.com/tesseract-ocr/tessdata_fast) 下载。

**Q: 图片审查结果总是"没有问题"？**

A: 确认 OCR 初始化成功（查看启动日志），并检查 `config/sensitive-words/` 中是否包含要检测的敏感词。

**Q: 如何修改审查规则？**

A: 编辑 `src/main/resources/application.yml` 中 `compliance` 下的参数，重启服务生效。

---

## 许可证

仅供学习和内部使用。
