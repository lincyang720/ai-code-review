# 安装与配置

## 前置条件

| 要求 | 说明 | 检查 |
|---|---|---|
| JDK 17+ | 必需 | `java -version` |
| Node.js 18+ | 仅 npm 安装方式需要 | `node -v` |
| 网络 | **不需要**（工具本身零外部依赖） | — |
| API Key | **不需要** | — |

> 工具是 38KB 的单jar，完全离线运行。安装阶段需要网络（拉 npm 包），之后再也不需要。

---

## 方式一：npm 全局安装（推荐）

生成 shim，任意目录可直接调用。

```bash
cd <ai-code-review-cli 目录>
npm install -g .
```

验证：

```bash
ai-code-review --version     # 应输出 ai-code-review 1.0.0
ai-code-review --help
```

**卸载**：

```bash
bash scripts/uninstall.sh
# 或
npm uninstall -g ai-code-review
```

---

## 方式二：手动放置（不依赖 npm）

适合内网环境、npm 不可用、或不想污染全局node 目录。

```bash
bash scripts/install.sh --manual
```

该脚本会提示你把 `<包目录>/bin` 加入 PATH。

**Windows 用户**：把 `<包目录>\bin` 加入系统 PATH（`系统属性 → 环境变量 → Path`）。

手动模式下调用形式是完整路径：

```bash
/完整路径/bin/ai-code-review.sh <项目> ./out
```

---

## 从源码安装

如果拿到了源码（`ai-code-review-demo` 目录）：

```bash
cd ai-code-review-demo
mvn clean package

# 把产物拷进 CLI 包
mkdir -p ../ai-code-review-cli/lib
cp target/review.jar ../ai-code-review-cli/lib/review.jar

cd ../ai-code-review-cli
npm install -g .
```

---

## 配置项

### 环境变量

| 变量 | 作用 | 示例 |
|---|---|---|
| `AI_REVIEW_JAVA_OPTS` | 传给 JVM 的额外参数 | `export AI_REVIEW_JAVA_OPTS=-Xmx4g` |
| `JAVA_HOME` | 指定 JDK位置（java 不在 PATH 时） | `export JAVA_HOME=/path/to/jdk17` |

### 常用配置建议

| 项目规模 | 推荐配置 |
|---|---|
| < 500 文件 | 默认即可 |
| 500 ~ 3000 文件 | `AI_REVIEW_JAVA_OPTS=-Xmx2g` |
| > 3000 文件 | `AI_REVIEW_JAVA_OPTS=-Xmx4g` |

---

## 安装验证清单

```bash
# 1. 版本可读
ai-code-review --version

# 2. 帮助可读
ai-code-review --help

# 3. 坏代码样本能被检出（预期 24 条 / P0 7）
ai-code-review <包目录>/samples/bad-code /tmp/t1

# 4. 规范代码零误报（预期 0 条）
ai-code-review <包目录>/samples/good-code /tmp/t2
```

四条全过即安装成功。

---

## 常见问题

**Q：`ai-code-review` 提示找不到命令**
npm 全局 bin 目录不在 PATH。查`npm config get prefix`，确认该目录已加入系统 PATH 后重开终端。

**Q：Windows 控制台中文乱码**
启动器已内置 `-Dfile.encoding=UTF-8`。若仍乱码，先执行 `chcp 65001` 切换终端代码页。

**Q：大项目报 OutOfMemoryError**
加内存：`AI_REVIEW_JAVA_OPTS=-Xmx4g`。

**Q：`.cmd` 启动器报奇怪的错、出现乱码碎片**
`.cmd` 文件必须是**纯 ASCII**。非 ASCII 注释在 GBK 代码页下会导致批处理解析崩溃 —— 这是改造过程中实际踩到的坑，注释已全部改为英文。