# hccloud Java 线技术债治理清单

> 数据来源：`ai-code-review` 扫描 4753 个 Java 文件（2026-10-10）
> 报告位置：`D:/work/code/hccloud/_review/`
> 本清单用于回答两个问题：**先改哪些？改了值多少钱？**

---

## 一、总体数据

| 级别 | 数量 | 本轮是否处理 |
|---|---|---|
| P0 阻断 | 0 | 无需处理 |
| **P1 必改** | **193** | ✅ **本轮目标** |
| P2 建议 | 1208 | 排期，不卡合并 |
| P3 提示 | 69 | 顺手改 |

**结论：P0 = 0 说明现有流程是健康的。本轮只盯 193 条 P1。**

---

## 二、P1 的真实构成（决定能不能自动化）

| 类型 | 数量 | 占比 | 能否自动修 |
|---|---|---|---|
| `printStackTrace` 丢日志 | 115 | 60% | ✅ **可自动改** |
| 空 catch 吞异常 | 63 | 33% | ⚠️ **半自动**（需人工确认是否该抛） |
| 关键事件用 debug 级别 | 15 | 8% | ❌ **需人工判断** |

**178 条（92%）高度可机械修复，只有 15 条真正需要人判断。**

---

## 三、集中度：这是最有价值的信息

### 按模块（治理顺序）

| 模块 | P1 数量 | 建议 |
|---|---|---|
| **hccloud-iot** | 67 | 🔴 **第一批** |
| **hccloud-streaming-media** | 46 | 🔴 **第一批** |
| hccloud-special-projects | 31 | 🟡 第二批 |
| hccloud-equipment | 17 | 🟡 第二批 |
| 其余 8 个模块 | 32 | 🟢 排期 |

> **只做前两个模块 = 覆盖 113/193 = 59% 的 P1。**

### 按文件（真正的抓手）

193 条 P1 分布在 **仅 49 个文件**里。最集中的：

| 文件 | 条数 |
|---|---|
| `RedisUtil2.java` | **26** |
| `DateUtils.java` | 8 |
| `FileToZip.java` | 5 |
| `SysSyncDataServiceImpl.java` | 4 |
| `WebJsInfoSocketHandler.java` | 3 |

> **单改一个 `RedisUtil2.java` 就干掉 26 条（13%）。**

---

## 四、四步治理路径

### 第 1 步：先看清（已完成）

```bash
cd D:/work/code/hccloud
AI_REVIEW_JAVA_OPTS=-Xmx2g ai-code-review . ./_review
```

产出本清单。**不要跳过这一步** —— 没有基线，后面无法证明改完了。

### 第 2 步：跑通一个模块（iot，1 周）

挑 `hccloud-iot` 的 67 条 P1：

1. **先改 `RedisUtil2.java`（26 条）** —— 单文件验证修法是否有效
2. 每改完一个文件重新扫一次，看数字下降
3. 提交时让 CI 扫改动文件，防止新增

**这一周的核心产出不是"改完 67 条"，而是"验证这套修法可行"。**

### 第 3 步：确认价值（关键，别跳过）

改完 iot 模块后，看两个数：

| 指标 | 怎么取 | 意义 |
|---|---|---|
| **PR 合并时长** | GitLab 统计 | 这就是哈佛研究里 +49% 的那项 |
| **返工 PR 占比** | PR 需修改数 / 总 PR | 这就是"近乎翻倍"的那项 |

> **这是唯一能证明 ROI 的数据。** 有了它，第 4 周才能谈推广。

### 第 4 步：铺开（streaming-media + 其余）

前两步验证有效后再铺。不要一次全上。

---

## 五、修法速查

### printStackTrace（115 条）

```java
// ❌ 之前：输出不进日志系统，故障时看不到
} catch (Exception e) {
    e.printStackTrace();
}

// ✅ 之后：结构化日志，带 traceId
} catch (Exception e) {
    log.error("Redis 连接失败, host={}, port={}", host, port, e);
}
```

要点：
- 异常对象作为**最后一个参数**传入，不要拼进字符串
- 带上关键业务标识（host、orderId 之类），否则日志等于没打
- 需要类上有 `@Slf4j`（Lombok）或手动 `LoggerFactory.getLogger()`

### 空 catch（63 条）

```java
// ❌ 之前：故障静默消失
} catch (Exception e) {
}

// ✅ 方案 A：确实该抛
} catch (Exception e) {
    throw new BizException("操作失败, id=" + id, e);
}

// ✅ 方案 B：确实可忽略，写明原因
} catch (NumberFormatException e) {
    // 上游可能返回空串，契约允许为空
}
```

⚠️ **这 63 条不要无脑批量改。** 有些 catch 是有意的（框架契约要求忽略）。
改之前先确认：这个异常真的可以吞掉吗？

### 关键事件用 debug（15 条）

```java
// ❌ 之前：线上默认只开 info，这条日志不会落盘
log.debug("用户登录失败, userId={}", userId);

// ✅ 之后
log.info("用户登录失败, userId={}", userId);
```

关键词：登录、支付、退款、删除、权限、注册、资金、对账、结算。
**这类日志丢失往往在出事那天才发现没有。**

---

## 六、执行清单

- [x] 第 1 步：全量基线扫描
- [ ] 第 2 步：iot 模块 67 条（先改 RedisUtil2.java 的 26 条验证修法）
- [ ] 第 3 步：取 PR 合并时长 / 返工率对比数据
- [ ] 第 4 步：streaming-media 46 条
- [ ] 第 5 步：其余模块 32 条 + 1208 条 P2 排期

---

## 七、防止反弹

改完之后一定要防新增，否则三个月后又是原样：

```bash
# CI 里扫改动文件，不做全量
ai-code-review ./src ./_review --enable NORM-002
```

**注意**：CI 里不要开 `--all-rules`（噪音会淹没信号）。
建议策略：P0 阻断合并，P1 只提醒不阻断（P1 通常是历史存量，全量阻断会让人一次面对几百条，直接放弃）。

详见 `docs/CI.md`。