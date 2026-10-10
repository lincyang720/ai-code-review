---
name: ai-code-review
agent_created: true
description: 对 Java 项目做确定性代码评审，输出可追溯的分级报告（安全/异常/日志/依赖/规范），并生成可直接回帖到 GitLab MR 的 Markdown。当用户说「跑一下代码评审」「扫一下这个项目有没有代码问题」「评审这个 Java 项目」「提交前自检」「代码质量基线扫描」「帮我看下项目里的 printStackTrace / 硬编码密码 / 高危依赖」「给这个 MR 挑毛病」「生成代码评审报告」「用ai-code-review 扫一下」时触发。基于哈佛 × Jellyfish700 家公司的研究（AI 编码 Agent 使代码量 +30% 但交付量未变，PR 合并时长 +49%）—— 本工具接管「有明确对错」的部分，把人的时间留给「需要判断」的部分。
---

# AI 代码评审流水线

## Overview

一句话：**它不写代码，它替人"挑毛病"。**

对应哈佛 × Jellyfish 研究的那个瓶颈 —— AI 写代码快了 30%，但公司交付没多，
PR 合并时长反而 +49%。因为瓶颈不在写代码，在**评审**。

本工具把评审里"有明确对错"的部分自动化，输出三份材料：
- 控制台摘要 → 给决策者
- `review-report.md` → 给技术负责人
- `gitlab-comment.md` → 给工程师（可直接 POST 到 MR）

## 何时用、何时不用

**该用**：
- 提交代码前自检
- 对客户项目做基线扫描（**最有说服力的销售动作**，10 分钟出报告）
- 全量扫描摸清技术债分布
- CI 阶段自动拦截（配 `.gitlab-ci.yml`，见 docs/CI.md）

**不该用**：
- 判断架构好坏 → 机器判不了
- 判断业务逻辑对不对 → 机器判不了
- 判断需求理解有没有偏差 → 机器判不了

**这四条边界写在报告正文里，不要替工具扩大宣传。**

## 用法

```bash
# 基本用法（全局命令，任意目录可用）
ai-code-review <扫描目标> [输出目录] [选项]

# 示例
ai-code-review ./src ./out                        扫描当前项目
ai-code-review D:/work/myproject ./out            扫描指定项目
ai-code-review ./src ./out --enable NORM-002      额外开启格式检查
ai-code-review --help                              帮助
ai-code-review --version                           版本号

# 环境变量
AI_REVIEW_JAVA_OPTS=-Xmx4g    传额外 JVM 参数（大项目必加）
```

**运行前提**：JDK 17+ 在 PATH 中。不需要网络，不需要 API Key。

## 规则集

| 规则 | 类别 | 覆盖 | 默认 |
|---|---|---|---|
| `SEC-001` | 安全 | 硬编码凭证、云厂商 Token 形态（sk-/ghp_/AKIA） | 开 |
| `EXC-001` | 异常 | 空 catch、printStackTrace、catch 内丢 cause、裸 RuntimeException | 开 |
| `DEP-001` | 依赖 | 已知高危版本、重复依赖声明 | 开 |
| `LOG-001` | 日志 | 字符串拼接、System.out、关键业务事件用 debug | 开 |
| `NORM-001` | 规范 | 方法过长、类过长、TODO 密度 | 开 |
| `NORM-002` | 规范 | 超长行 | **关**（噪音大，用 `--enable` 开） |

**实测数据**（hccloud 4753 文件）：命中 375 文件 = 7.1%，1470 条问题，
其中 P0=0 / P1=193。经三轮误报调优（11697 → 1470，降 87%）后 P0/P1 真实信号零损失。

## 给 AI 助手的工作流建议

当用户让你"用这个工具扫一下"时，按这个顺序做：

1. **先确认 JDK 可用**：`java -version`，没有则直接告知用户，不要尝试其他方案
2. **大项目加内存**：`AI_REVIEW_JAVA_OPTS=-Xmx4g`，否则 4000+ 文件可能 OOM
3. **全量扫描用默认规则集**，不要开 `--all-rules`（噪音会淹没信号）
4. **读 `review-report.md` 做归类**，不要只看控制台摘要就给结论
5. **重点关注 P0/P1**，P2/P3 属于技术债，不是痛点

### 分析结果时的注意点

- **P0=0 不代表工具没干活**，可能项目本身安全。诚实说"该拦的都拦住了"
- **P1 才是商业论据**。尤其 EXC-001 的 printStackTrace / 空 catch，
  代价不在写代码时，在故障发生的那个凌晨
- **分布集中度比总量更有信息量**。先做 P1 最高的两个模块，能覆盖大部分问题
- **不要把全部 1470 条都报给用户**。按级别和模块聚合后给结论

## 误报调优经验（重要，直接影响可信度）

首轮扫描 hccloud 是 **11697 条**，客户看到这个数字会直接放弃。三次关键调优：

| 优化项 | 前| 后 | 手段 |
|---|---|---|---|
| 超长行 | 7985 条（68%） | 默认关闭 | 格式问题归 IDE 插件，不该占评审通道 |
| catch 内丢 cause | 误报 2241 条 | 2 条 | 正则不区分作用域 → 增加 catch 作用域判定 |
| 字段名当值 | 3 条假 P0 | 0 条 | `PASSWORD = "password"` → 变量名与值相同判定 |

> **评审工具的核心竞争力不是"规则多"，而是"噪音低"。**
> 报11697 条和报 1470 条，在客户那里的命运完全不同。

**如果客户项目噪音大，第一反应应该是调规则，而不是让客户接受。**

## 目录结构

```
ai-code-review-cli/
├── SKILL.md              本文件：给 AI 助手看的定义
├── package.json          npm 全局安装配置
├── bin/
│   ├── ai-code-review.sh    Bash 启动器（自我定位，不依赖工作目录）
│   └── ai-code-review.cmd   Windows 启动器
├── lib/
│   └── review.jar         核心引擎（38KB，零外部依赖）
├── scripts/
│   ├── install.sh        安装脚本
│   ├── uninstall.sh      卸载脚本
│   └── scan-ci.sh        CI 集成示例
├── docs/
│   ├── INSTALL.md        安装配置说明
│   ├── USAGE.md          参数与示例
│   ├── ARCHITECTURE.md   架构与设计决策
│   ├── CHANGELOG.md      变更记录
│   └── CI.md             GitLab CI / Jenkins 集成
└── samples/
    ├── bad-code/         故意写坏的样本（预期 24 条）
    └── good-code/        规范代码样本（预期 0 条）
```

## 相关文档

- 安装：`docs/INSTALL.md`
- 参数与示例：`docs/USAGE.md`
- 架构与设计取舍：`docs/ARCHITECTURE.md`
- 变更记录：`docs/CHANGELOG.md`
- CI 集成：`docs/CI.md`