package com.tkevinb.ragent.infra.chat;

import com.tkevinb.ragent.framework.convention.ChatRequest;
import com.tkevinb.ragent.infra.enums.ModelProvider;
import com.tkevinb.ragent.infra.model.ModelTarget;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

/**
 * Ollama ChatClient 实现（OpenAI 兼容协议）
 * <p>
 * 要求 Ollama >= 0.3.0，支持 /v1/chat/completions 端点。
 */
@Slf4j
@Service
public class OllamaChatClient extends AbstractOpenAIStyleChatClient {

    public OllamaChatClient(OkHttpClient syncHttpClient,
                            OkHttpClient streamingHttpClient,
                            Executor modelStreamExecutor) {
        super(syncHttpClient, streamingHttpClient, modelStreamExecutor);
    }

    @Override
    public String provider() {
        return ModelProvider.OLLAMA.getId();
    }

    @Override
    protected boolean requiresApiKey() {
        return false;
    }

    @Override
    public String chat(ChatRequest request, ModelTarget target) {
        return doChat(request, target);
    }

    @Override
    public StreamCancellationHandle streamChat(ChatRequest request, StreamCallBack callback, ModelTarget target) {
        return doStreamChat(request, callback, target);
    }
}
