package com.tkevinb.ragent.infra.model;

import com.tkevinb.ragent.infra.chat.ChatClient;
import com.tkevinb.ragent.infra.chat.StreamCallBack;
import com.tkevinb.ragent.infra.chat.StreamCancellationHandle;
import com.tkevinb.ragent.infra.config.AIModelProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 模型路由执行器 — 统一的 fallback 模板
 * <p>
 * 同步调用和流式调用复用同一套健康检查 + 遍历降级逻辑。
 */
@Slf4j
@Component
public class ModelRoutingExecutor {

    private final AIModelProperties modelProperties;
    private final Map<String, ChatClient> clientsByProvider;
    private final ModelHealthStore healthStore;

    public ModelRoutingExecutor(AIModelProperties modelProperties,
                                List<ChatClient> clients,
                                ModelHealthStore healthStore) {
        this.modelProperties = modelProperties;
        this.clientsByProvider = clients.stream()
                .collect(java.util.stream.Collectors.toMap(ChatClient::provider, Function.identity()));
        this.healthStore = healthStore;
    }

    /** 按优先级排序候选模型 */
    public List<ModelTarget> selectCandidates() {
        return modelProperties.getChat().getCandidates().stream()
                .filter(c -> c.getEnabled() != null && c.getEnabled())
                .sorted(java.util.Comparator.comparingInt(
                        c -> c.getPriority() != null ? c.getPriority() : 100))
                .map(c -> new ModelTarget(c.getId(), c,
                        modelProperties.getProviders().get(c.getProvider())))
                .toList();
    }

    /**
     * 同步调用模板
     */
    public <R> R executeWithFallback(
            List<ModelTarget> targets,
            BiFunction<ChatClient, ModelTarget, R> call) {
        Exception lastError = null;
        for (ModelTarget target : targets) {
            if (!healthStore.allowCall(target.id())) continue;
            ChatClient client = clientsByProvider.get(target.candidate().getProvider());
            if (client == null) continue;
            try {
                R result = call.apply(client, target);
                healthStore.markSuccess(target.id());
                return result;
            } catch (Exception e) {
                lastError = e;
                healthStore.markFailure(target.id());
                log.warn("模型 {} 调用失败: {}", target.id(), e.getMessage());
            }
        }
        throw new IllegalStateException("所有模型调用失败" +
                (lastError != null ? ": " + lastError.getMessage() : ""));
    }

    /**
     * 流式调用模板（含首包探测）
     */
    public StreamCancellationHandle executeStreamWithFallback(
            List<ModelTarget> targets,
            TriFunction<ChatClient, ModelTarget, ProbeStreamBridge, StreamCancellationHandle> call,
            StreamCallBack userCallback,
            long firstPacketTimeoutMs) {

        for (ModelTarget target : targets) {
            if (!healthStore.allowCall(target.id())) continue;
            ChatClient client = clientsByProvider.get(target.candidate().getProvider());
            if (client == null) continue;

            ProbeStreamBridge bridge = new ProbeStreamBridge(userCallback);
            try {
                StreamCancellationHandle handle = call.apply(client, target, bridge);
                if (handle == null) {
                    healthStore.markFailure(target.id());
                    continue;
                }

                // 等待首包
                ProbeStreamBridge.ProbeResult result = bridge.awaitFirstPacket(firstPacketTimeoutMs);
                if (result == ProbeStreamBridge.ProbeResult.SUCCESS) {
                    healthStore.markSuccess(target.id());
                    return handle; // 首包已到，后续由原始 handle 继续
                }

                healthStore.markFailure(target.id());
                handle.cancel();
                log.warn("模型 {} 流式首包超时, 切换下一个", target.id());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                healthStore.markFailure(target.id());
            } catch (Exception e) {
                healthStore.markFailure(target.id());
                log.warn("模型 {} 流式启动失败: {}", target.id(), e.getMessage());
            }
        }

        userCallback.onError(new IllegalStateException("所有模型流式调用失败"));
        return () -> {};
    }

    @FunctionalInterface
    public interface TriFunction<A, B, C, R> {
        R apply(A a, B b, C c);
    }
}
