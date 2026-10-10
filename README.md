# ai-code-review

> AI 代码评审流水线 —— 把评审里"有明确对错"的部分自动化，
> 把人的时间留给"需要判断"的部分。

对应哈佛 × Jellyfish 研究的发现：AI 编码 Agent 让代码量 +30%，
但公司交付量**没有增加**，PR 合并时长反而 **+49%**。
原因不在写代码，在**评审**。

---

## 快速开始

```bash
# 方式一：clone 后直接用（jar 已提交，无需 Maven）
git clone https://github.com/lincyang720/ai-code-review.git
cd ai-code-review && npm install -g.

# 方式二：从源码构建
git clone https://github.com/lincyang720/ai-code-review.git
cd ai-code-review
mvn -f engine/pom.xml clean package
cp engine/target/review.jar lib/
npm install -g.
```

验证：

```bash
ai-code-review --help
```

**前提**：JDK 17+ 在 PATH 中。不需要网络，不需要 API Key。

---

## 从源码构建（开发者）

```bash
mvn -f engine/pom.xml clean package     # 构建到 engine/target/review.jar
cp engine/target/review.jar lib/        # 更新 CLI 用的 jar
npm install -g .                        # 重新安装
```

GitHub Actions 会在每次 push 时自动跑一遍自检：
坏代码样本必须是 24 条、规范代码样本必须是 0 条，对不上就判定为规则回归。

---

## 目录结构

```
ai-code-review-cli/
├── SKILL.md                   AI 助手加载的定义（何时用、怎么用、注意事项）
├── package.json               npm 全局安装配置
├── README.md                  本文件
│
├── bin/                       启动器：自我定位包根，不依赖工作目录
│   ├── ai-code-review.sh         Bash / Git Bash
│   └── ai-code-review.cmd        Windows（含 npm 生成的 shim 转发）
│
├── lib/
│   └── review.jar             核心引擎，38KB，零外部依赖（已提交，clone 即可用）
│
├── engine/                    引擎源码（Maven 工程）
│   ├── pom.xml
│   └── src/main/java/com/autumn/review/
│       ├── ReviewCli.java         CLI 入口
│       ├── engine/                引擎 + 领域模型
│       ├── rules/                 6 条规则
│       ├── llm/                   LLM 增强接口
│       └── report/                三份报告生成
│
├── scripts/
│   ├── install.sh             安装脚本（含 Java 前置检查）
│   ├── uninstall.sh           卸载脚本
│   └── scan-ci.sh             CI 扫描封装（含 P0 阻断策略）
│
├── docs/
│   ├── INSTALL.md             安装配置详解
│   ├── USAGE.md               参数、示例、退出码
│   ├── ARCHITECTURE.md        架构与设计取舍（含数据）
│   ├── CHANGELOG.md           变更记录
│   └── CI.md                  GitLab CI / Jenkins / GitHub Actions
│
└── samples/                   可直接运行的对照样本
    ├── bad-code/                故意写坏 → 预期 24 条
    └── good-code/               规范代码 → 预期 0 条
```

### 各文件职责

| 文件 | 职责 | 何时被用到 |
|---|---|---|
| `bin/*.sh` / `*.cmd` | 解析自身位置 → 推导包根 → 定位 jar → 检查 java → 转发 | 每次调用 |
| `lib/review.jar` | 全部评审逻辑（引擎 + 6 条规则 + 三份报告生成） | 每次调用 |
| `SKILL.md` | 告诉 AI 助手：何时触发、怎么调、怎么解读结果 | AI 助手加载时 |
| `scripts/install.sh` | 检查 Java 版本、检查 jar、npm 安装、验证 | 安装时 |
| `scripts/scan-ci.sh` | 大项目加内存、P0 计数、按策略决定是否阻断 | CI 中 |
| `docs/*` | 参数、架构、CI 配置、变更记录 | 按需查阅 |
| `samples/*` | 验证零误报 + 演示用 | 演示、回归测试 |

---

## 两种形态的取舍

原始交付是**单 jar**，改造后是**CLI + Skill**。为什么不只留 jar：

| | jar 形态 | CLI + Skill 形态 |
|---|---|---|
| 调用方式 | `java -jar xxx.jar <路径>` | `ai-code-review <路径>` |
| 路径依赖 | 需记住 jar 在哪 | 任意目录 |
| AI 可用性 | 需在prompt 里解释命令与参数 | `SKILL.md` 自带用法与判读指引 |
| 分发 | 传文件 | npm / git clone |
| 前置检查 | 无，靠人 | 启动器检查 java、检查 jar |
| 版本识别 | 看文件名 | `ai-code-review --version` |

**结论：jar 是内核，CLI 是入口，SKILL.md 是说明书。** 三层都要，缺一层就少一批用户。

---

## 规则集

| 规则 | 类别 | 覆盖 | 默认 |
|---|---|---|---|
| `SEC-001` | 安全 | 硬编码凭证、云厂商 Token 形态 | 开 |
| `EXC-001` | 异常 | 空 catch、printStackTrace、catch 内丢 cause、裸 RuntimeException | 开 |
| `DEP-001` | 依赖 | 已知高危版本、重复依赖 | 开 |
| `LOG-001` | 日志 | 字符串拼接、`System.out`、关键事件用 debug | 开 |
| `NORM-001` | 规范 | 方法过长、类过长、TODO 密度 | 开 |
| `NORM-002` | 规范 | 超长行 | **关** |

### 实测数据

| 场景 | 文件 | 问题 | 命中率 |
|---|---|---|---|
| `samples/bad-code` | 2 | 24（P0 7 / P1 5 / P2 12） | — |
| `samples/good-code` | 1 | **0** | 零误报 |
| 某 Spring Cloud 多模块项目 | 4753 | 1470（P0 0 / P1 193 / P2 1208 / P3 69） | 375 文件 = 7.1% |

首轮扫描该真实项目产出 11697 条，经三轮误报调优降至 1470 条（**-87%**），
P0/P1 真实信号零损失。详见 `docs/CHANGELOG.md`。

---

## 明确的能力边界

**工具不做**（这四条写死在报告正文里）：

- 判断架构与设计取舍
- 判断业务逻辑正确性
- 判断需求理解偏差
- 判断跨模块契约一致性

**工具做**：安全、异常、日志、依赖、结构规范——**有明确对错**的部分。

---

## 常用命令

```bash
ai-code-review --help
ai-code-review --version
ai-code-review ./src ./out                          # 扫描当前项目
ai-code-review D:/work/project ./out                # 扫描指定项目
AI_REVIEW_JAVA_OPTS=-Xmx4g ai-code-review . ./out   # 大项目加内存
ai-code-review ./src ./out --enable NORM-002        # 开启格式检查
```

更多见 `docs/USAGE.md`。

---

## 卸载

```bash
bash scripts/uninstall.sh
# 或
npm uninstall -g ai-code-review
```

源码保留在包目录，可重新安装。