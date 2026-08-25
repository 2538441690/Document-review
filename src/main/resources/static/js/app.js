(function () {
    'use strict';

    // ==================== DOM 元素 ====================
    const uploadZone = document.getElementById('uploadZone');
    const fileInput = document.getElementById('fileInput');
    const selectedFile = document.getElementById('selectedFile');
    const selectedFileName = document.getElementById('selectedFileName');
    const selectedFileSize = document.getElementById('selectedFileSize');
    const fileIcon = document.getElementById('fileIcon');
    const btnRemove = document.getElementById('btnRemove');
    const btnUpload = document.getElementById('btnUpload');
    const btnClear = document.getElementById('btnClear');
    const uploadProgress = document.getElementById('uploadProgress');
    const progressFill = document.getElementById('progressFill');
    const progressText = document.getElementById('progressText');
    const resultSection = document.getElementById('resultSection');
    const issuesList = document.getElementById('issuesList');
    const verdict = document.getElementById('verdict');
    const verdictIcon = document.getElementById('verdictIcon');
    const verdictText = document.getElementById('verdictText');
    const healthStatus = document.getElementById('healthStatus');
    const healthText = document.getElementById('healthText');

    let currentFile = null;

    // ==================== 初始化 ====================
    checkHealth();

    // ==================== 文件类型图标映射 ====================
    function getFileIcon(fileName) {
        var ext = fileName.split('.').pop().toLowerCase();
        var icons = {
            docx: '\u{1F4C4}', doc: '\u{1F4C4}',
            xlsx: '\u{1F4CA}', xls: '\u{1F4CA}',
            pdf: '\u{1F4D5}',
            png: '\u{1F5BC}', jpg: '\u{1F5BC}', jpeg: '\u{1F5BC}',
            bmp: '\u{1F5BC}', gif: '\u{1F5BC}', tiff: '\u{1F5BC}'
        };
        return icons[ext] || '\u{1F4CE}';
    }

    function formatFileSize(bytes) {
        if (bytes < 1024) return bytes + ' B';
        if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
        return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
    }

    // ==================== 健康检查 ====================
    function checkHealth() {
        fetch('/api/document/health')
            .then(function (r) { return r.json(); })
            .then(function (data) {
                if (data.status === 'UP') {
                    healthStatus.className = 'status-dot online';
                    healthText.textContent = '系统运行正常' + (data.wordLibraryLoaded ? ' · 词库已加载' : '');
                } else {
                    healthStatus.className = 'status-dot offline';
                    healthText.textContent = '系统异常';
                }
            })
            .catch(function () {
                healthStatus.className = 'status-dot offline';
                healthText.textContent = '服务连接失败';
            });
    }

    // ==================== 文件选择 ====================
    function selectFile(file) {
        if (!file) return;
        currentFile = file;
        selectedFileName.textContent = file.name;
        selectedFileSize.textContent = formatFileSize(file.size);
        fileIcon.textContent = getFileIcon(file.name);
        selectedFile.style.display = 'flex';
        uploadZone.classList.add('has-file');
        btnUpload.disabled = false;
        hideResult();
    }

    function clearFile() {
        currentFile = null;
        fileInput.value = '';
        selectedFile.style.display = 'none';
        selectedFileName.textContent = '';
        selectedFileSize.textContent = '';
        uploadZone.classList.remove('has-file');
        btnUpload.disabled = true;
        hideUploadProgress();
        hideResult();
    }

    function hideResult() {
        resultSection.style.display = 'none';
        issuesList.innerHTML = '';
    }

    // ==================== 上传区域事件 ====================
    uploadZone.addEventListener('click', function () {
        fileInput.click();
    });

    uploadZone.addEventListener('dragover', function (e) {
        e.preventDefault();
        uploadZone.classList.add('drag-over');
    });

    uploadZone.addEventListener('dragleave', function (e) {
        e.preventDefault();
        uploadZone.classList.remove('drag-over');
    });

    uploadZone.addEventListener('drop', function (e) {
        e.preventDefault();
        uploadZone.classList.remove('drag-over');
        var files = e.dataTransfer.files;
        if (files.length > 0) {
            selectFile(files[0]);
        }
    });

    fileInput.addEventListener('change', function () {
        if (fileInput.files.length > 0) {
            selectFile(fileInput.files[0]);
        }
    });

    btnRemove.addEventListener('click', function (e) {
        e.stopPropagation();
        clearFile();
    });

    btnClear.addEventListener('click', function () {
        clearFile();
    });

    // ==================== 上传进度 ====================
    function showUploadProgress() {
        uploadProgress.style.display = 'block';
        progressFill.style.width = '0%';
    }

    function updateUploadProgress(percent) {
        progressFill.style.width = percent + '%';
        progressText.textContent = '正在审查中... ' + percent + '%';
    }

    function hideUploadProgress() {
        uploadProgress.style.display = 'none';
        progressFill.style.width = '0%';
    }

    // ==================== 上传审查 ====================
    btnUpload.addEventListener('click', function () {
        if (!currentFile) return;

        showUploadProgress();
        btnUpload.disabled = true;
        var formData = new FormData();
        formData.append('file', currentFile);

        // 模拟进度
        var progress = 0;
        var progressTimer = setInterval(function () {
            progress += 10;
            if (progress > 90) {
                clearInterval(progressTimer);
            }
            updateUploadProgress(progress);
        }, 100);

        fetch('/api/document/review', {
            method: 'POST',
            body: formData
        })
            .then(function (response) {
                clearInterval(progressTimer);
                return response.json();
            })
            .then(function (data) {
                updateUploadProgress(100);
                setTimeout(function () {
                    hideUploadProgress();
                    displayResult(data);
                    btnUpload.disabled = false;
                }, 300);
            })
            .catch(function (err) {
                clearInterval(progressTimer);
                hideUploadProgress();
                btnUpload.disabled = false;
                alert('审查请求失败: ' + err.message);
            });
    });

    // ==================== 显示结果 ====================
    function displayResult(data) {
        resultSection.style.display = 'block';

        // 概要卡片
        document.getElementById('valDuration').textContent = (data.durationMs || 0) + 'ms';
        document.getElementById('valTotal').textContent = data.totalIssues || 0;
        document.getElementById('valErrors').textContent = data.errorCount || 0;
        document.getElementById('valWarnings').textContent = data.warningCount || 0;
        document.getElementById('valInfos').textContent = data.infoCount || 0;

        // 结论
        if (data.totalIssues === 0 && data.infoCount === 0) {
            verdict.className = 'verdict pass';
            verdictIcon.textContent = '\u2705';
        } else if (data.errorCount > 0) {
            verdict.className = 'verdict fail';
            verdictIcon.textContent = '\u274C';
        } else if (data.warningCount > 0) {
            verdict.className = 'verdict warn';
            verdictIcon.textContent = '\uD83D\uDCA1';
        } else {
            verdict.className = 'verdict info';
            verdictIcon.textContent = '\u2705';
        }
        verdictText.textContent = data.summary;

        // 问题列表
        issuesList.innerHTML = '';
        if (data.issues && data.issues.length > 0) {
            data.issues.forEach(function (issue) {
                issuesList.appendChild(createIssueCard(issue));
            });
        } else {
            issuesList.innerHTML = '<div style="text-align:center;padding:40px;color:#718096;">' +
                '\u2705 未发现任何问题，文档合规</div>';
        }

        // 滚动到结果
        resultSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }

    function createIssueCard(issue) {
        var card = document.createElement('div');
        card.className = 'issue-card ' + issue.severity;
        card.setAttribute('data-severity', issue.severity);

        var severityNames = { ERROR: '错误', WARNING: '警告', INFO: '提示' };
        var categoryNames = {
            SENSITIVE_WORD: '敏感词',
            GRAMMAR: '语法',
            TEXT_FORMAT: '文本格式',
            IMAGE_FORMAT: '图片格式',
            TABLE_FORMAT: '表格格式',
            OCR: 'OCR识别'
        };

        var severityLabel = severityNames[issue.severity] || issue.severity;
        var categoryLabel = categoryNames[issue.category] || issue.category;

        var html = '<span class="issue-severity">' + escapeHtml(severityLabel) + '</span>';
        html += '<div class="issue-body">';
        html += '<div class="issue-description">' + escapeHtml(issue.description) + '</div>';
        html += '<div class="issue-meta">';
        html += '<span>\u{1F4CB} ' + escapeHtml(categoryLabel) + '</span>';
        if (issue.suggestion) {
            html += '<span>\u{1F4A1} ' + escapeHtml(issue.suggestion) + '</span>';
        }
        html += '</div>';
        html += '</div>';
        html += '<div class="issue-location">' + escapeHtml(issue.location || '') + '</div>';

        card.innerHTML = html;
        return card;
    }

    // ==================== 过滤 ====================
    var filterTabs = document.querySelectorAll('.filter-tab');
    filterTabs.forEach(function (tab) {
        tab.addEventListener('click', function () {
            filterTabs.forEach(function (t) { t.classList.remove('active'); });
            tab.classList.add('active');
            var filter = tab.getAttribute('data-filter');
            var cards = issuesList.querySelectorAll('.issue-card');
            cards.forEach(function (card) {
                if (filter === 'all' || card.getAttribute('data-severity') === filter) {
                    card.style.display = '';
                } else {
                    card.style.display = 'none';
                }
            });
        });
    });

    // ==================== 工具函数 ====================
    function escapeHtml(str) {
        if (!str) return '';
        var div = document.createElement('div');
        div.appendChild(document.createTextNode(str));
        return div.innerHTML;
    }
})();