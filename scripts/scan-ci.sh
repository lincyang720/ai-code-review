#!/usr/bin/env bash
# CI 集成示例：扫描本次改动涉及的目录
#
# GitLab CI 用法（在 .gitlab-ci.yml 中）：
#   ai-review:
#     stage: review
#     image: eclipse-temurin:17-jdk
#     script:
#       - bash scripts/scan-ci.sh "$CI_PROJECT_DIR" "$CI_MERGE_REQUEST_IID" \
#           -o /tmp/review
#       - COMMENT=$(cat /tmp/review/gitlab-comment.md)
#       - curl -s -X POST
#           "$CI_API_V4_URL/projects/$CI_PROJECT_ID/merge_requests/$CI_MERGE_REQUEST_IID/notes"
#           -H "PRIVATE-TOKEN: $CI_JOB_TOKEN"
#           --data-urlencode "body=$COMMENT"

set -euo pipefail

TARGET="${1:-.}"
MR_IID="${2:-}"
shift 2 2>/dev/null || shift 1
OUT_DIR="./review-out"

# 解析额外参数
while [ $# -gt 0 ]; do
  case "$1" in
    -o|--out) OUT_DIR="$2"; shift 2 ;;
    --enable) enable="$2"; shift 2 ;;
    *) shift ;;
  esac
done

echo "[ci] 扫描目标: $TARGET"
echo "[ci] 输出目录: $OUT_DIR"

# 大项目给足内存，否则 4000+ 文件可能 OOM
export AI_REVIEW_JAVA_OPTS="${AI_REVIEW_JAVA_OPTS:--Xmx2g}"

CMD=(ai-code-review "$TARGET" "$OUT_DIR")
[ -n "${enable:-}" ] && CMD+=(--enable "$enable")

if ! "${CMD[@]}"; then
  echo "[ci] 工具执行失败（可能是环境问题），不阻断流水线"
  exit 0
fi

# 阻断策略：存在 P0 时以非零码退出，可配置为"只警告"
if [ -f "$OUT_DIR/review-report.md" ]; then
  P0_COUNT=$(grep -c '^- 级别：P0$' "$OUT_DIR/review-report.md" 2>/dev/null || echo 0)
  echo "[ci] P0 数量: $P0_COUNT"
  if [ "${BLOCK_ON_P0:-0}" = "1" ] && [ "$P0_COUNT" -gt 0 ]; then
    echo "[ci] 存在 P0 阻断项，按策略阻断"
    exit 1
  fi
fi

echo "[ci] 完成。报告: $OUT_DIR/review-report.md"