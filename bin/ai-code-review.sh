#!/usr/bin/env bash
# ai-code-review —— 全局 CLI 启动器
#
# 设计目标：在任意目录直接调用，不依赖固定工作目录。
# 做法：脚本自身位置 -> 推导包根目录 -> 定位 lib/review.jar
#
# 安装后由 npm shim 转发到本脚本，通常你直接敲 `ai-code-review <路径>` 即可。

set -euo pipefail

# --- 定位包根目录（本脚本真实路径，兼容 symlink） ---
SOURCE="${BASH_SOURCE[0]}"
while [ -L "$SOURCE" ]; do
  DIR="$(cd -P "$(dirname "$SOURCE")" >/dev/null 2>&1 && pwd)"
  SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE"
done
BIN_DIR="$(cd -P "$(dirname "$SOURCE")" >/dev/null 2>&1 && pwd)"
PKG_ROOT="$(cd -P "$BIN_DIR/.." >/dev/null 2>&1 && pwd)"

JAR="$PKG_ROOT/lib/review.jar"

# --- 依赖检查：给清晰报错，而不是让用户看 stacktrace ---
if ! command -v java >/dev/null 2>&1; then
  echo "错误：未找到 java。请安装 JDK 17 或更高版本，并确认 java 在 PATH 中。" >&2
  exit 127
fi

if [ ! -f "$JAR" ]; then
  echo "错误：未找到 $JAR" >&2
  echo "请重新安装，或在项目根目录执行：mvn clean package" >&2
  exit 2
fi

# --- 前置检查 ---
if [ "$#" -eq 0 ]; then
  exec java -jar "$JAR" --help
fi

case "$1" in
  -h|--help|help)
    exec java -jar "$JAR" --help
    ;;
  -v|--version|version)
    # 版本号从 jar 内的 manifest 读，避免双份维护
    exec java -jar "$JAR" --version
    ;;
esac

# -Dfile.encoding=UTF-8 必须加，否则 Windows 控制台中文乱码
exec java -Dfile.encoding=UTF-8 ${AI_REVIEW_JAVA_OPTS:-} -jar "$JAR" "$@"