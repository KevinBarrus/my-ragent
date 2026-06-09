package com.tkevinb.ragent.infra.chat;

import com.tkevinb.ragent.framework.convention.ChatRequest;
import com.tkevinb.ragent.infra.config.AIModelProperties;
import com.tkevinb.ragent.infra.model.ModelHealthStore;
import com.tkevinb.ragent.infra.model.ModelTarget;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 路由式 LLM 服务 — 健康检查 + 多模型 fallback
 * <p>
 * 按优先级遍历候选模型，跳过熔断的模型，调用成功则返回，
 * 失败则标记并尝试下一个。全部失败返回兜底错误。
 */
@Slf4j
@Service
@Primary
public class RoutingLLMService implements LLMService {

    private final AIModelProperties modelProperties;
    private final Map<String, ChatClient> clientsByProvider;
    private final ModelHealthStore healthStore;

    public RoutingLLMService(AIModelProperties modelProperties,
                              List<ChatClient> clients,
                              ModelHealthStore healthStore) {
        this.modelProperties = modelProperties;
        this.clientsByProvider = clients.stream()
                .collect(Collectors.toMap(ChatClient::provider, Function.identity()));
        this.healthStore = healthStore;
    }

    @Override
    public String chat(ChatRequest request) {
        List<ModelTarget> targets = selectCandidates();
        Exception lastError = null;

        for (ModelTarget target : targets) {
            if (!healthStore.allowCall(target.id())) {
                log.info("模型 {} 已熔断，跳过", target.id());
                continue;
            }

            ChatClient client = clientsByProvider.get(target.candidate().getProvider());
            if (client == null) {
                log.warn("未找到提供商客户端: {}", target.candidate().getProvider());
                continue;
            }

            try {
                String result = client.chat(request, target);
                healthStore.markSuccess(target.id());
                return result;
            } catch (Exception e) {
                lastError = e;
                boolean tripped = healthStore.markFailure(target.id());
                log.warn("模型 {} 调用失败, trip={}: {}", target.id(), tripped, e.getMessage());
            }
        }

        throw new IllegalStateException("所有模型调用失败" +
                (lastError != null ? ": " + lastError.getMessage() : ""));
    }

    @Override
    public StreamCancellationHandle streamChat(ChatRequest request, StreamCallBack callback) {
        List<ModelTarget> targets = selectCandidates();
        Exception lastError = null;

        for (ModelTarget target : targets) {
            if (!healthStore.allowCall(target.id())) continue;

            ChatClient client = clientsByProvider.get(target.candidate().getProvider());
            if (client == null) continue;

            try {
                StreamCancellationHandle handle = client.streamChat(request, callback, target);
                healthStore.markSuccess(target.id());
                return handle;
            } catch (Exception e) {
                lastError = e;
                healthStore.markFailure(target.id());
            }
        }

        callback.onError(new IllegalStateException("所有模型调用失败" +
                (lastError != null ? ": " + lastError.getMessage() : "")));
        return () -> {};
    }

    /** 按优先级排序候选模型 */
    private List<ModelTarget> selectCandidates() {
        return modelProperties.getChat().getCandidates().stream()
                .filter(c -> c.getEnabled() != null && c.getEnabled())
                .sorted(Comparator.comparingInt(c -> c.getPriority() != null ? c.getPriority() : 100))
                .map(c -> {
                    AIModelProperties.ProviderConfig provider =
                            modelProperties.getProviders().get(c.getProvider());
                    return new ModelTarget(c.getId(), c, provider);
                })
                .toList();
    }
}
