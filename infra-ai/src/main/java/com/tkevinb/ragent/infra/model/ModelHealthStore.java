package com.tkevinb.ragent.infra.model;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 模型健康状态管理
 * <p>
 * 记录每个候选模型的失败次数和熔断窗口。
 * 连续失败 threshold 次 → 熔断 openDurationMs 毫秒 → 到期后允许重试。
 */
@Slf4j
@Component
public class ModelHealthStore {

    private final int failureThreshold;
    private final long openDurationMs;
    private final Map<String, Node> nodes = new ConcurrentHashMap<>();

    public ModelHealthStore(
            @Value("${ai.selection.failure-threshold:2}") int failureThreshold,
            @Value("${ai.selection.open-duration-ms:30000}") long openDurationMs) {
        this.failureThreshold = failureThreshold;
        this.openDurationMs = openDurationMs;
    }

    /** 允许调用：未达熔断阈值 */
    public boolean allowCall(String modelId) {
        Node node = nodes.get(modelId);
        if (node == null) return true;
        if (node.get() >= failureThreshold) {
            long elapsed = System.currentTimeMillis() - node.lastFailTime;
            if (elapsed < openDurationMs) return false; // 仍在熔断期
            node.reset(); // 熔断到期，复位
        }
        return true;
    }

    /** 标记成功 */
    public void markSuccess(String modelId) {
        nodes.remove(modelId);
    }

    /** 标记失败，返回是否触发熔断 */
    public boolean markFailure(String modelId) {
        Node node = nodes.computeIfAbsent(modelId, k -> new Node());
        int count = node.incrementAndGet();
        node.lastFailTime = System.currentTimeMillis();
        if (count >= failureThreshold) {
            log.warn("模型 {} 连续失败 {} 次，熔断 {}ms", modelId, count, openDurationMs);
            return true;
        }
        return false;
    }

    private static class Node {
        final AtomicInteger counter = new AtomicInteger(0);
        volatile long lastFailTime;

        int get() { return counter.get(); }
        int incrementAndGet() { return counter.incrementAndGet(); }
        void reset() { counter.set(0); lastFailTime = 0; }
    }
}
