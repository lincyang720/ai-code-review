package com.good.sample;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 对照样本：符合规范的写法。
 *
 * <p>用于演示"规则不误报"——扫描这个文件应当得到 0 条问题。
 * 拿掉规则测试，这个文件会被报出 5 条噪音，那这套工具就没人愿意用了。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderRepository orderRepository;

    private static final int MAX_PAGE_SIZE = 100;

    /**
     * 查询订单详情。
     *
     * @param orderId 订单号
     * @param userId  用户号
     * @return 订单详情
     * @throws OrderNotFoundException 订单不存在时抛出
     */
    public OrderDetail queryDetail(String orderId, String userId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        log.info("查询订单详情成功, orderId={}, userId={}", orderId, userId);
        return OrderDetail.from(order);
    }

    /** 分页查询，单页上限 100 条。 */
    public Page<OrderSummary> pageQuery(String userId, int page, int size) {
        int safeSize = Math.min(size, MAX_PAGE_SIZE);
        return orderRepository.pageByUserId(userId, page, safeSize);
    }
}