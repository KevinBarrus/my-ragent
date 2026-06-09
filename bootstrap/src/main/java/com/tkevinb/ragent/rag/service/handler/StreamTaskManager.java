package com.tkevinb.ragent.rag.service.handler;

import com.tkevinb.ragent.framework.web.SseEmitterSender;
import com.tkevinb.ragent.infra.chat.StreamCancellationHandle;
import com.tkevinb.ragent.rag.dto.CompletionPayload;
import com.tkevinb.ragent.rag.enums.SSEEventType;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 流式任务管理器（Redisson 分布式版）
 * <p>
 * 取消状态存 Redis，跨实例共享。
 * JVM 本地对象（Handle/Sender）仍用 ConcurrentHashMap。
 */
@Slf4j
@Component
public class StreamTaskManager {

    private static final String KEY_PREFIX = "ragent:task:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final RedissonClient redisson;
    private final Map<String, LocalTask> localTasks = new ConcurrentHashMap<>();

    public StreamTaskManager(RedissonClient redisson) {
        this.redisson = redisson;
    }

    public void register(String taskId, SseEmitterSender sender, Supplier<CompletionPayload> onCancelSupplier) {
        localTasks.computeIfAbsent(taskId, k -> new LocalTask()).sender = sender;
        localTasks.get(taskId).onCancelSupplier = onCancelSupplier;
    }

    public void bindHandle(String taskId, StreamCancellationHandle handle) {
        LocalTask local = localTasks.computeIfAbsent(taskId, k -> new LocalTask());
        local.handle = handle;
        if (isCancelled(taskId) && handle != null) {
            handle.cancel();
        }
    }

    public boolean isCancelled(String taskId) {
        RBucket<String> bucket = redisson.getBucket(KEY_PREFIX + taskId);
        return "cancelled".equals(bucket.get());
    }

    public void cancel(String taskId) {
        // 1. 写 Redis → 所有实例可见
        RBucket<String> bucket = redisson.getBucket(KEY_PREFIX + taskId);
        bucket.set("cancelled", TTL);

        // 2. 取消本地 handle（如果在本实例上）
        LocalTask local = localTasks.get(taskId);
        if (local == null) {
            log.info("任务 {} 不在本实例，Redis 已标记取消，远端实例将感知", taskId);
            return;
        }

        if (local.handle != null) {
            local.handle.cancel();
        }
        if (local.sender != null) {
            CompletionPayload payload = local.onCancelSupplier != null
                    ? local.onCancelSupplier.get()
                    : new CompletionPayload(null, null);
            local.sender.sendEvent(SSEEventType.CANCEL.value(), payload);
            local.sender.sendEvent(SSEEventType.DONE.value(), "[DONE]");
            local.sender.complete();
        }
    }

    public void unregister(String taskId) {
        localTasks.remove(taskId);
        redisson.getBucket(KEY_PREFIX + taskId).delete();
    }

    private static class LocalTask {
        volatile StreamCancellationHandle handle;
        volatile SseEmitterSender sender;
        volatile Supplier<CompletionPayload> onCancelSupplier;
    }
}
