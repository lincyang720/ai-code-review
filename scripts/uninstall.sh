#!/usr/bin/env bash
# ai-code-review 卸载脚本
set -euo pipefail

PKG_ROOT="$(cd -P "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CMD_NAME="ai-code-review"

echo "正在卸载 ai-code-review..."

if command -v npm >/dev/null 2>&1; then
  npm uninstall -g "$PKG_ROOT" 2>&1 | tail -3 || \
  npm uninstall -g ai-code-review 2>&1 | tail -3 || true
fi

if command -v "$CMD_NAME" >/dev/null 2>&1; then
  echo -e "\033[0;33m⚠ 命令仍存在于 PATH：$(command -v "$CMD_NAME")\033[0m"
  echo "  请手动检查该文件所属目录是否为已删除的包目录。"
else
  echo "✓ 已卸载"
fi

echo ""
echo "源码仍保留在：$PKG_ROOT"
echo "如需彻底删除，请手动移除该目录。"