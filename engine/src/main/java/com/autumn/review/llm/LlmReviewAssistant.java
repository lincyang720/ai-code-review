package com.autumn.review.llm;

import com.autumn.review.engine.Finding;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM 增强层（可选）。
 *
 * <p>这里刻意<b>不绑定任何具体 SDK</b>（不引 Spring AI、不引 openai-java），
 * 只定义接口。原因是落地时的真实约束：
 * <ul>
 *   <li>客户代码不能外发（源码是核心资产）→ 需要能切到本地模型</li>
 *   <li>模型选型各家不同 → 不该在工具里锁死</li>
 * </ul>
 *
 * <p>LLM 在这套流水线里只做<b>一件事</b>：把规则命中翻译成"这个项目的业务后果"。
 * 它<b>不负责</b>判断代码对错——那是规则的活。所以 LLM 挂了不影响主流程。
 */
public interface LlmReviewAssistant {

    /**
     * 对确定性规则命中的问题，补充业务影响说明。
     *
     * @param finding   规则命中项
     * @param context   项目背景（技术栈、业务域等），可为 null
     * @return 补充说明；不需要补充时返回 null
     */
    String explainBusinessImpact(Finding finding, String projectContext);

    /**
     * 默认实现：什么都不做。可直接用于离线演示。
     */
    LlmReviewAssistant NOOP = (finding, ctx) -> null;

    /**
     * 组合执行：LLM 不可用时静默降级，绝不阻断评审主流程。
     */
    static LlmReviewAssistant safe(LlmReviewAssistant delegate) {
        return (finding, ctx) -> {
            try {
                return delegate.explainBusinessImpact(finding, ctx);
            } catch (RuntimeException e) {
                // 降级：LLM 不可用不影响评审
                return null;
            }
        };
    }

    /**
     * 把 LLM 输出批量合并进报告。
     */
    static List<String> batchExplain(List<Finding> findings, LlmReviewAssistant assistant,
                                     String projectContext) {
        List<String> out = new ArrayList<>();
        if (assistant == null) {
            return out;
        }
        for (Finding f : findings) {
            String s = safe(assistant).explainBusinessImpact(f, projectContext);
            if (s != null && !s.isBlank()) {
                out.add(f.ruleId() + " @ " + f.filePath() + " → " + s);
            }
        }
        return out;
    }
}