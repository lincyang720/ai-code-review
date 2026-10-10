# CI 集成

## GitLab CI

### 方式一：直接写进 `.gitlab-ci.yml`

```yaml
stages: [review]

ai-code-review:
  stage: review
  image: eclipse-temurin:17-jdk
  # 只在 MR 触发，且跳过草稿
  rules:
    - if: $CI_PIPELINE_SOURCE == "merge_request_event" && $CI_MERGE_REQUEST_DRAFT == "false"
  variables:
    GIT_DEPTH: 0        # 需要完整历史才能做增量对比
    AI_REVIEW_JAVA_OPTS: "-Xmx2g"
  script:
    - |
      # 获取工具（三种方式任选其一）
      # A) 从内网制品库下载
      wget -qO /tmp/review.jar "https://your-internal-artifacts/review.jar"
      # B) 从项目子模块
      # B) wget -qO /tmp/review.jar "$CI_PROJECT_DIR/tools/review.jar"
      # C) 用 CI 缓存
      # C) cp $CI_PROJECT_DIR/tools/review.jar /tmp/

    - |
      java -Dfile.encoding=UTF-8 -jar /tmp/review.jar \
        "$CI_PROJECT_DIR" ./review-out

    # 报告留档
    - cat ./review-out/review-report.md
    - |
      COMMENT=$(cat ./review-out/gitlab-comment.md)
      curl -s -X POST \
        "$CI_API_V4_URL/projects/$CI_PROJECT_ID/merge_requests/$CI_MERGE_REQUEST_IID/notes" \
        -H "PRIVATE-TOKEN: $CI_JOB_TOKEN" \
        --data-urlencode "body=$COMMENT"
  artifacts:
    when: always
    paths: [review-out/]
    expire_in: 1 week
```

### 方式二：阻断合并（有P0 就失败）

在上述基础上加一步：

```yaml
    - |
      P0=$(grep -c '^- 级别：P0$' ./review-out/review-report.md || echo 0)
      if [ "$P0" -gt 0 ]; then
        echo "发现 $P0 个 P0 阻断项，终止合并"
        exit 1
      fi
```

> **建议策略**：P0 阻断合并，P1 只提醒不阻断。
> P1 通常是历史存量，全量阻断会让团队一次性面对几百条，直接放弃工具。

### 增量扫描（推荐）

全量扫描噪音大（存量问题会淹没本次改动）。只扫改动文件：

```yaml
    - |
      BASE=$(git merge-base HEAD origin/$CI_MERGE_REQUEST_TARGET_BRANCH_NAME)
      git diff --name-only --diff-filter=ACM $BASE HEAD \
        | grep '\.java$' > changed.txt || true
      if [ -s changed.txt ]; then
        java -Dfile.encoding=UTF-8 -jar /tmp/review.jar \
          $(dirname $(head -1 changed.txt)) ./review-out
      fi
```

---

## Jenkins

```groovy
pipeline {
  agent any
  stages {
    stage('AI Code Review') {
      when { changeRequest() }
      steps {
        sh '''
          java -Dfile.encoding=UTF-8 -jar tools/review.jar \
            "$WORKSPACE" ./review-out
        '''
        // 归档报告
        archiveArtifacts artifacts: 'review-out/*.md', allowEmptyArchive: true

        // 读回注释并发到 MR（需配置 Change Request 插件）
        script {
          def comment = readFile('./review-out/gitlab-comment.md')
          if (env.CHANGE_REQUEST_ID) {
            sh """
              curl -s -X POST \
                '${env.CHANGE_REQUEST_API_URL}/notes' \
                -H 'PRIVATE-TOKEN: ${gitlabToken}' \
                --data-urlencode 'body=${comment}'
            """
          }
        }
      }
    }
  }
}
```

---

## GitHub Actions

```yaml
name: AI Code Review
on: pull_request

jobs:
  review:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - name: Download review tool
        run: wget -qO /tmp/review.jar "https://your-artifacts/review.jar"
      - name: Run review
        run: java -Dfile.encoding=UTF-8 -jar /tmp/review.jar "$GITHUB_WORKSPACE" ./review-out
      - name: Comment on PR
        uses: actions/github-script@v7
        with:
          script: |
            const fs = require('fs');
            const body = fs.readFileSync('./review-out/gitlab-comment.md', 'utf8');
            await github.rest.issues.createComment({
              owner: context.repo.owner,
              repo: context.repo.repo,
              issue_number: context.issue.number,
              body
            });
```

---

## 落地节奏建议

别一上来全量强制。

| 阶段 | 动作 | 目的 |
|---|---|---|
| 第 1 周 | 只生成报告，**不阻断、不评论** | 让团队先看到全貌 |
| 第 2–3 周 | 只在 1~2 个模块开，评论进 MR | 试水，收集噪音反馈 |
| 第 4 周 | 加 P0 阻断 | 开始产生约束力 |
| 第 5 周起 | 全量 + 规则库按团队规范定制 | 沉淀成标准 |

**第 4 周务必导出「PR 合并时长 / 返工率」的前后对比** —— 这是唯一能证明 ROI 的硬数据。

---

## 常见问题

**Q：CI 里没有 `ai-code-review` 全局命令**
CI 环境是干净的容器，全局安装不会保留。用 jar 直调（上面示例都是这种方式）。

**Q：报告里路径是容器内的绝对路径**
用相对路径调用，或在生成后`sed` 替换前缀。

**Q：扫描太慢拖垮 CI**
只扫改动文件（增量模式），别做全量。4753 文件全量约 60 秒，增量通常 5 秒内。