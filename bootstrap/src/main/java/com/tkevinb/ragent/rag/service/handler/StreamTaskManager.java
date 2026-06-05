package com.tkevinb.ragent.rag.service.handler;

import com.tkevinb.ragent.framework.web.SseEmitterSender;
import com.tkevinb.ragent.infra.chat.StreamCancellationHandle;
import com.tkevinb.ragent.rag.dto.CompletionPayload;
import com.tkevinb.ragent.rag.enums.SSEEventType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * 流式任务管理器（简化版）
 * <p>
 * MVP 阶段使用本地内存管理任务，不依赖 Redis。
 * 后续分布式部署时可替换为 Redisson 版本。
 */
@Slf4j
@Component
public class StreamTaskManager {

    private final Map<String, StreamTaskInfo> tasks = new ConcurrentHashMap<>();

    public void register(String taskId, SseEmitterSender sender, Supplier<CompletionPayload> onCancelSupplier) {
        StreamTaskInfo info = tasks.computeIfAbsent(taskId, k -> new StreamTaskInfo());
        info.sender = sender;
        info.onCancelSupplier = onCancelSupplier;
    }

    public void bindHandle(String taskId, StreamCancellationHandle handle) {
        StreamTaskInfo info = tasks.computeIfAbsent(taskId, k -> new StreamTaskInfo());
        info.handle = handle;
        if (info.cancelled.get() && handle != null) {
            handle.cancel();
        }
    }

    public boolean isCancelled(String taskId) {
        StreamTaskInfo info = tasks.get(taskId);
        return info != null && info.cancelled.get();
    }

    public void cancel(String taskId) {
        StreamTaskInfo info = tasks.get(taskId);
        if (info == null || !info.cancelled.compareAndSet(false, true)) {
            return;
        }
        if (info.handle != null) {
            info.handle.cancel();
        }
        if (info.sender != null) {
            CompletionPayload payload = info.onCancelSupplier != null
                    ? info.onCancelSupplier.get()
                    : new CompletionPayload(null, null);
            info.sender.sendEvent(SSEEventType.CANCEL.value(), payload);
            info.sender.sendEvent(SSEEventType.DONE.value(), "[DONE]");
            info.sender.complete();
        }
    }

    public void unregister(String taskId) {
        tasks.remove(taskId);
    }

    private static final class StreamTaskInfo {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile StreamCancellationHandle handle;
        private volatile SseEmitterSender sender;
        private volatile Supplier<CompletionPayload> onCancelSupplier;
    }
}
