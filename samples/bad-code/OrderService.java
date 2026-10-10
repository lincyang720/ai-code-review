package com.bad.sample;

import java.io.PrintWriter;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections.functors.Transformer;
import java.util.HashMap;
import java.util.Map;

/**
 * 演示样本：故意写坏的文件。
 *
 * <p>覆盖五类规则的全部触发点，用来向客户演示"哪些问题会被自动拦下来"。
 * 请勿在生产环境使用本文件。
 */
public class OrderService {

    // SEC-001：硬编码数据库口令
    private String dbPassword = "SuperSecret2024";

    // SEC-001：硬编码 API Key
    private String apiKey = "sk-live-abcdef123456ghijklmnop";

    // NORM-001：魔法数字
    private int maxRetry = 3;
    private long timeout = 30000;

    private Map<String, String> cache = new HashMap<String, String>();

    /**
     * 核心下单方法：刻意写了过长 + 缺异常处理 + 裸 RuntimeException
     */
    public String createOrder(String userId, String productId, int quantity, String couponCode, String addressId, String remark, boolean usePoints, int pointToUse, String channel, String deviceId) {
        StringBuilder sb = new StringBuilder();
        sb.append("user=").append(userId);
        sb.append(",product=").append(productId);
        sb.append(",qty=").append(quantity);
        sb.append(",coupon=").append(couponCode);
        sb.append(",addr=").append(addressId);
        sb.append(",remark=").append(remark);
        sb.append(",usePoints=").append(usePoints);
        sb.append(",pointToUse=").append(pointToUse);
        sb.append(",channel=").append(channel);
        sb.append(",device=").append(deviceId);

        // LOG-001：字符串拼接
        System.out.println("创建订单 " + sb.toString());

        // LOG-001：拼接 + System.out
        PrintWriter out = new PrintWriter(System.out);
        out.println("订单创建中..." + userId);
        out.flush();

        try {
            // EXC-001：业务失败抛裸 RuntimeException
            if (quantity <= 0) {
                throw new RuntimeException("数量必须大于0");
            }

            // EXC-001：catch 后什么都不做（吞异常）
            try {
                validateStock(productId, quantity);
            } catch (Exception e) {
            }

            // SEC-001：token 形态凭证
            String token = "ghp_abcdefghijklmnopqrstuvwxyz1234";

            // LOG-001：关键业务事件用 debug
            log.debug("用户 " + userId + " 下单支付, 金额=" + computeAmount(productId, quantity));

            // EXC-001：printStackTrace
            try {
                deductStock(productId, quantity);
            } catch (Exception e) {
                e.printStackTrace();
            }

            // EXC-001：rethrow 丢 cause
            try {
                processPayment(userId, productId, quantity);
            } catch (PaymentException e) {
                throw new RuntimeException("支付失败");
            }

            cache.put(userId + productId, "OK");

        } catch (RuntimeException e) {
            // EXC-001：rethrow 不带 cause，异常链断裂
            throw new RuntimeException("创建订单失败");
        }

        return sb.toString();
    }

    /**
     * 同步支付回调：故意用 System.out + printStackTrace
     */
    public void onPayCallback(String orderId, int amount) {
        System.out.println("收到支付回调 orderId=" + orderId + " amount=" + amount);

        // LOG-001：拼接日志
        log.info("支付成功 订单:" + orderId + " 金额:" + amount);

        // LOG-001：关键业务事件（删除/权限类）用 debug
        log.debug("删除支付流水, orderId=" + orderId);

        try {
            verifySign(orderId, amount);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 超长行演示
     */
    public String buildQueryResult(String userId, String orderId, String productName, String category, int quantity, double unitPrice, double discount, double taxRate, String shippingAddress, String receiverName, String receiverPhone, String remark, String createdAt, String updatedAt, String status, String paymentMethod, String logisticsCompany, String trackingNo) {
        return userId + orderId + productName + category + quantity + unitPrice + discount + taxRate + shippingAddress + receiverName + receiverPhone + remark + createdAt + updatedAt + status + paymentMethod + logisticsCompany + trackingNo;
    }

    // TODO: 改成统一的异常体系
    // TODO: 补单元测试
    // TODO: 缓存加 TTL
    // FIXME: 这里有并发问题

    private double computeAmount(String productId, int qty) {
        return qty * 99.0;
    }

    private void validateStock(String productId, int qty) throws Exception {
    }

    private void deductStock(String productId, int qty) throws Exception {
    }

    private void processPayment(String u, String p, int q) throws PaymentException {
    }

    private void verifySign(String orderId, int amount) {
    }

    private final Logger log = new Logger();

    static class PaymentException extends Exception {
    }

    /** 极简日志占位，实际项目用 slf4j */
    static class Logger {
        void debug(String s) {
        }

        void info(String s) {
        }
    }
}