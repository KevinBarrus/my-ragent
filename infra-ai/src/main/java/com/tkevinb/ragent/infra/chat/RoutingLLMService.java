package com.tkevinb.ragent.infra.chat;

import com.tkevinb.ragent.framework.convention.ChatRequest;
import com.tkevinb.ragent.infra.model.ModelRoutingExecutor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 路由式 LLM 服务
 * <p>
 * 委托 ModelRoutingExecutor 处理 fallback + 流式首包探测。
 */
@Primary
@Service
public class RoutingLLMService implements LLMService {

    private final ModelRoutingExecutor executor;

    public RoutingLLMService(ModelRoutingExecutor executor) {
        this.executor = executor;
    }

    @Override
    public String chat(ChatRequest request) {
        return executor.executeWithFallback(
                executor.selectCandidates(),
                (client, target) -> client.chat(request, target)
        );
    }

    @Override
    public StreamCancellationHandle streamChat(ChatRequest request, StreamCallBack callback) {
        return executor.executeStreamWithFallback(
                executor.selectCandidates(),
                (client, target, bridge) -> client.streamChat(request, bridge, target),
                callback,
                60_000  // 首包超时 60 秒
        );
    }
}
