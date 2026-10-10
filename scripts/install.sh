#!/usr/bin/env bash
# ai-code-review 安装脚本
#
# 两种安装方式：
#   1) npm 全局安装（推荐，生成 shim，任何目录可调用）
#      cd <此目录> && npm install -g .
#   2) 手动放置 shim（不依赖 npm）
#      bash scripts/install.sh --manual

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m'

PKG_ROOT="$(cd -P "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CMD_NAME="ai-code-review"

echo "========================================"
echo " ai-code-review 安装程序"
echo "========================================"
echo ""

# --- 前置检查 ---
if ! command -v java >/dev/null 2>&1; then
  echo -e "${RED}✗ 未找到 java${NC}"
  echo "  请安装 JDK 17 或更高版本，并确认 java 在 PATH 中。"
  exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -1)
echo -e "${GREEN}✓${NC} Java: ${JAVA_VERSION}"

JAVA_MAJOR=$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')
if [ "$JAVA_MAJOR" -lt 17 ]; then
  echo -e "${YELLOW}⚠ 建议使用 JDK 17+，当前为 ${JAVA_MAJOR}${NC}"
fi

if [ ! -f "$PKG_ROOT/lib/review.jar" ]; then
  echo -e "${RED}✗ 未找到 lib/review.jar${NC}"
  echo "  请先在源码目录执行：mvn clean package"
  exit 1
fi
echo -e "${GREEN}✓${NC} 核心 jar: $(ls -lh "$PKG_ROOT/lib/review.jar" | awk '{print $5}')"
echo ""

# --- 安装方式 ---
if [ "${1:-}" = "--manual" ]; then
  # 手动模式：直接在包目录创建可执行入口
  chmod +x "$PKG_ROOT/bin/ai-code-review.sh"
  echo -e "${YELLOW}手动模式${NC}"
  echo "  请把以下路径加入 PATH："
  echo "    export PATH=\"$PKG_ROOT/bin:\$PATH\""
  echo ""
  echo "  Windows 用户可把 $PKG_ROOT\\bin 加入系统 PATH"
  exit 0
fi

if ! command -v npm >/dev/null 2>&1; then
  echo -e "${YELLOW}未找到 npm，回退到手动模式${NC}"
  echo "  请把 $PKG_ROOT/bin 加入 PATH"
  exit 0
fi

echo "正在执行 npm 全局安装..."
if npm install -g "$PKG_ROOT" 2>&1 | tail -5; then
  echo ""
  if command -v "$CMD_NAME" >/dev/null 2>&1; then
    echo -e "${GREEN}✓ 安装成功${NC}"
    echo "  现在可以在任意目录运行："
    echo "    ${CMD_NAME} --help"
    echo "    ${CMD_NAME} <你的项目路径> ./out"
  else
    echo -e "${YELLOW}安装完成，但当前 shell 找不到 ${CMD_NAME}${NC}"
    echo "  npm 全局 bin 目录可能不在 PATH，请检查："
    echo "    npm config get prefix"
    echo "  确认该目录已加入系统 PATH 后重开终端。"
  fi
else
  echo -e "${RED}✗ npm 安装失败${NC}"
  echo "  可尝试手动模式：bash scripts/install.sh --manual"
  exit 1
fi