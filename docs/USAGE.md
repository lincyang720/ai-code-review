# 使用说明 · 参数与示例

## 命令格式

```
ai-code-review <扫描目标> [输出目录] [选项]
```

全局命令，**任意目录可调用**，不依赖固定工作目录。

---

## 参数

| 位置 | 参数 | 必填 | 说明 |
|---|---|---|---|
| 第1 位 | `<扫描目标>` | 是 | 项目目录或单个文件路径。支持相对路径、绝对路径 |
| 第 2 位 | `[输出目录]` | 否 | 报告输出目录，默认 `./out` |
| 选项 | `--enable <规则号>` | 否 | 开启默认关闭的规则 |
| 选项 | `--all-rules` | 否 | 开启全部规则（含格式类，噪音大） |
| 选项 | `-v` / `--version` | — | 显示版本号 |
| 选项 | `-h` / `--help` | — | 显示帮助 |

---

## 规则编号

| 编号 | 类别 | 内容 | 默认状态 |
|---|---|---|---|
| `SEC-001` | 安全 | 硬编码凭证、云厂商 Token 形态（`sk-` / `ghp_` / `AKIA`） | 开启 |
| `EXC-001` | 异常 | 空 catch、`printStackTrace`、catch 内丢 cause、裸 RuntimeException | 开启 |
| `DEP-001` | 依赖 | 已知高危版本、重复依赖声明 | 开启 |
| `LOG-001` | 日志 | 字符串拼接、`System.out`、关键业务事件用 debug | 开启 |
| `NORM-001` | 规范 | 方法过长、类过长、TODO 密度 | 开启 |
| `NORM-002` | 规范 | 超长行（>120 字符） | **关闭** |

---

## 示例

### 基本用法

```bash
# 扫描当前目录下的 src
ai-code-review ./src ./out

# 扫描指定项目（相对路径）
ai-code-review ../my-project ./out

# 扫描指定项目（绝对路径）
ai-code-review D:/work/myproject ./out

# 用 . 代替扫描目标 = 扫描当前目录
ai-code-review . ./out
```

### 大项目

```bash
# Linux / macOS
AI_REVIEW_JAVA_OPTS=-Xmx4g ai-code-review D:/big-project ./out

# Windows CMD
set AI_REVIEW_JAVA_OPTS=-Xmx4g
ai-code-review D:\big-project .\out

# Windows PowerShell
$env:AI_REVIEW_JAVA_OPTS="-Xmx4g"
ai-code-review D:\big-project .\out
```

### 开启格式检查

```bash
# 只在增量扫描（改动文件）时用
ai-code-review ./src ./out --enable NORM-002

# 全量扫描时不要开，噪音会淹没信号
```

### 单文件扫描

```bash
ai-code-review src/main/java/com/example/OrderService.java ./out
```

---

## 输出物

每次扫描生成**三份**文件，各面向不同读者：

| 文件 | 读者 | 用途 |
|---|---|---|
| `<输出目录>/review-report.html` | **人（首选）** | 仪表盘 + 按级别折叠，可直接贴 issue / PR 评论 |
| `<输出目录>/review-report.md` | AI / diff | 供 AI 读取与版本对比 |
| `<输出目录>/gitlab-comment.md` | 工程师 | 可直接 POST 到 GitLab MR 评论的 Markdown |

控制台额外输出**拦截摘要**（分级统计 + 结论），面向决策者。

### HTML 报告怎么看

打开 `review-report.html` 后：

1. **先看顶部四张卡** —— P0/P1/P2/P3 各多少条，一眼判断严重程度
2. **看"干净度"** —— 「92% 干净」比「217 条问题」更能说明工程健康度
3. **看人工边界卡** —— 明确列出工具不覆盖的 4 类问题
4. **展开 P0/P1** —— 这两组默认展开，是真正需要立刻处理的
5. **P2/P3 默认收起** —— 需要时点开标题看单条

配色取自 `gzh-design` 技能的「石墨极简」主题设计变量，
实现在 Java 侧全内联样式，不依赖外部 CSS，可直接贴进 GitLab / 邮件。

---

## 分级含义

| 级别 | 含义 | 处置建议 |
|---|---|---|
| **P0 阻断** | 安全漏洞 / 依赖高危| 必须拦截合并 |
| **P1 必改** | 异常处理缺陷、日志缺失 | 合入前必须解决 |
| **P2 建议** | 结构性规范问题 | 排期整改，别卡合并 |
| **P3 提示** | 类过长、TODO 密度 | 顺手改 |

---

## 参考数据（实测基线）

### 样本对照

| 样本 | 文件 | P0 | P1 | P2 | P3 | 合计 |
|---|---|---|---|---|---|---|
| `samples/bad-code` | 2 | 7 | 5 | 12 | 0 | **24** |
| `samples/good-code` | 1 | 0 | 0 | 0 | 0 | **0** |

`good-code` 为 0 条 = **验证零误报**。这是工具可信度的前提。

### 真实项目

Spring Cloud 多模块企业应用，4753 个 Java 文件：

| 指标 | 值 |
|---|---|
| 发现问题 | 1470 条 |
| P0 / P1 / P2 / P3 | 0 / 193 / 1208 / 69 |
| 命中文件 | 375 / 4753 = **7.1%** |
| 扫描耗时 | 约 60 秒 |

---

## 接入 CI

见 `docs/CI.md`，含现成的 GitLab CI YAML 与 Jenkins 用法。

---

## 退出码

| 码 | 含义 |
|---|---|
| 0 | 正常完成 |
| 1 | 参数错误 / 扫描目标不存在 |
| 2 | review.jar 缺失（安装不完整） |
| 127 | 未找到 java |

**注意**：发现P0 问题时退出码仍为 0 —— 是否阻断合并由 CI 脚本自行判断
（参考 `scripts/scan-ci.sh` 的 `BLOCK_ON_P0` 开关）。