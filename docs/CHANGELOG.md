# 变更记录

本文件记录**影响使用者**的变更。内部重构不逐条记录，只在版本号变化时汇总。

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

---

## [1.0.0] - 2026-10-10

首个正式版本。基于 某真实项目 4753 个Java 文件的实测基线。

### 新增

- **CLI 全局命令**：`npm install -g .` 后任意目录可直接调用 `ai-code-review`
- **`--version` / `--help`**：版本号从 jar manifest 读取，单一事实源
- **`AI_REVIEW_JAVA_OPTS`**：环境变量，传额外 JVM 参数（如 `-Xmx4g`）
- **`--enable <规则号>`**：开启默认关闭的规则（如 `NORM-002`）
- **`--all-rules`**：开启全部规则
- **Skill 定义**：`SKILL.md` + `docs/CI.md`，可被 AI 助手直接加载
- **CI 集成示例**：GitLab CI / Jenkins / GitHub Actions 三套现成配置
- **三份输出材料**：控制台摘要（决策者）、`review-report.md`（技术负责人）、
  `gitlab-comment.md`（工程师）

### 规则集

| 规则 | 类别 | 默认 |
|---|---|---|
| `SEC-001` 硬编码凭证 | 安全 | 开 |
| `EXC-001` 异常处理缺陷 | 异常 | 开 |
| `DEP-001` 依赖高危/重复 | 依赖 | 开 |
| `LOG-001` 日志问题 | 日志 | 开 |
| `NORM-001` 结构规范 | 规范 | 开 |
| `NORM-002` 超长行 | 规范 | **关** |

### 实测基线

- 样本：`bad-code` 24 条（P0 7 / P1 5 / P2 12）；`good-code` 0 条
- 真实项目（4753 文件）：1470 条，命中 375 文件 = 7.1%，P0 = 0

### 误报调优记录

首轮扫描该项目 产出 **11697 条**，经三轮调优降至 **1470 条（-87%）**，
P0/P1 真实信号零损失：

| 优化 | 前 | 后 | 手段 |
|---|---|---|---|
| 超长行 | 7985 条（68%） | 默认关闭 | 格式问题归 IDE 插件，不占评审通道 |
| catch 内丢 cause | 误报 2241 条 | 2 条 | 增加 catch 作用域判定，避免把顶层 throw 误判 |
| 字段名当值 | 3 条假 P0 | 0 条 | 新增「变量名与值相同」判定 |

### 已知限制

- 不做跨文件语义分析（规则基于文本匹配）
- 依赖黑名单为内置小清单，生产环境应替换为对接 CVE 数据库
- 7.1% 命中率为全量存量数据；增量模式下命中率更高、噪音更低

---

## 后续计划

- [ ] 增量扫描模式（只扫改动文件）
- [ ] 规则库外置为配置文件，支持按团队规范定制
- [ ] 对接 OWASP Dependency-Check / Trivy 替换内置依赖黑名单
- [ ] P1 常见问题的自动修复建议（当前只给方向，不改代码）
---

## 技术决策：为什么 bin 用 Node 而非 .sh / .cmd

npm 只有一个 `bin` 字段，**无法按平台选择入口**，这会造成死锁：

- 指向 `.cmd` → Linux 生成的 shim 会把 Windows 批处理当 shell 执行
  （实际踩到：CI 报 `@echo: command not found`）
- 指向 `.sh` → Windows 无法直接执行

**解法**：`bin` 指向 `bin/ai-code-review.js`（Node + shebang）。
Node 是本工具链除 JDK 外唯一的依赖，两个平台原生可用；
入口内部按 `process.platform` 转发到对应启动器。

Windows 下 `spawnSync` 执行 `.cmd` 必须加 `shell: true`，否则静默无输出。

### 跨平台验证结果

| 平台 | bad-code | good-code |
|---|---|---|
| Windows（本地） | 24 条（P0 7 / P1 5） | 0 条 |
| Linux（GitHub Actions） | 24 条（P0 7 / P1 5） | 0 条 |

---

## 修复：注释掉的代码被误报（真实项目发现）

### 问题

在 `hccloud-material-science`（617 文件）上扫描，5 条 P1 里**4 条是误报**——
都是被 `//` 注释掉的 `e.printStackTrace()`：

```java
} catch (Exception e) {
//                    e.printStackTrace();
}
```

规则按行做文本匹配，没有识别注释。**注释掉的代码根本不会执行**，报出来是纯噪音。

真实项目是工具最好的验收场——样例样本造不出这种case。

### 修复

`ExceptionHandlingRule` 与 `HardcodedSecretRule` 增加注释行跳过：
- `//` 单行注释
- `/*` `*/` 块注释首尾
- `*` 开头的块注释中间行

### 效果

| 项目 | 修复前 P1 | 修复后 P1 |
|---|---|---|
| hccloud-material-science | 5（4 误报） | **0** |

### 回归验证

修复只减误报、不减真问题：

| 样本 | 预期 | 实际 |
|---|---|---|
| samples/bad-code | 24 条 / P0 7 / P1 5 | 24 / 7 / 5 ✅ |
| samples/good-code | 0 条 | 0 ✅ |

### 附带教训

Java 源码注释里**不能出现 `*/` 字面量**，会提前闭合 Javadoc 导致编译失败。
本次又踩了一次（注释里写「块注释首尾行（/** 与 */）」）。
同理，中文全角括号在注释里没问题，但字符串里用全角引号会破坏语法。
