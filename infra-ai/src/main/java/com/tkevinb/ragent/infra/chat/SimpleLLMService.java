package com.tkevinb.ragent.infra.chat;

import com.tkevinb.ragent.framework.convention.ChatMessage;
import com.tkevinb.ragent.framework.convention.ChatRequest;
import com.tkevinb.ragent.infra.config.AIModelProperties;
import com.tkevinb.ragent.infra.enums.ModelCapability;
import com.tkevinb.ragent.infra.model.ModelTarget;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 简化版 LLM 服务
 * <p>
 * MVP 阶段直接选择配置中优先级最高的 chat 模型，
 * 不做多模型路由、健康检查、自动降级。
 */
@Slf4j
@Service
public class SimpleLLMService implements LLMService {

    private final AIModelProperties modelProperties;
    private final Map<String, ChatClient> clientsByProvider;

    public SimpleLLMService(AIModelProperties modelProperties, List<ChatClient> clients) {
        this.modelProperties = modelProperties;
        this.clientsByProvider = clients.stream()
                .collect(Collectors.toMap(ChatClient::provider, Function.identity()));
    }

    @Override
    public String chat(ChatRequest request) {
        ModelTarget target = selectTarget(false);
        ChatClient client = resolveClient(target);
        if (client == null) {
            throw new IllegalStateException("无可用的 chat 模型");
        }
        return client.chat(request, target);
    }

    @Override
    public StreamCancellationHandle streamChat(ChatRequest request, StreamCallBack callback) {
        ModelTarget target = selectTarget(Boolean.TRUE.equals(request.getThinking()));
        ChatClient client = resolveClient(target);
        if (client == null) {
            callback.onError(new IllegalStateException("无可用的 stream chat 模型"));
            return () -> {};
        }
        return client.streamChat(request, callback, target);
    }

    private ModelTarget selectTarget(boolean deepThinking) {
        List<AIModelProperties.ModelCandidate> candidates = modelProperties.getChat().getCandidates();
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("未配置 chat 模型");
        }
        // 选优先级最高的
        AIModelProperties.ModelCandidate candidate = candidates.get(0);
        AIModelProperties.ProviderConfig provider = modelProperties.getProviders().get(candidate.getProvider());
        if (provider == null) {
            throw new IllegalStateException("未找到提供商配置: " + candidate.getProvider());
        }
        return new ModelTarget(candidate.getId(), candidate, provider);
    }

    private ChatClient resolveClient(ModelTarget target) {
        ChatClient client = clientsByProvider.get(target.candidate().getProvider());
        if (client == null) {
            log.warn("提供商客户端缺失: provider={}", target.candidate().getProvider());
        }
        return client;
    }
}
