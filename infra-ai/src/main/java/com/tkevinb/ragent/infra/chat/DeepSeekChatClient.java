package com.tkevinb.ragent.infra.chat;

import com.tkevinb.ragent.framework.convention.ChatRequest;
import com.tkevinb.ragent.infra.model.ModelTarget;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

/**
 * DeepSeek ChatClient（OpenAI 兼容协议）
 */
@Slf4j
@Service
public class DeepSeekChatClient extends AbstractOpenAIStyleChatClient {

    public DeepSeekChatClient(OkHttpClient syncHttpClient,
                              OkHttpClient streamingHttpClient,
                              Executor modelStreamExecutor) {
        super(syncHttpClient, streamingHttpClient, modelStreamExecutor);
    }

    @Override
    public String provider() {
        return "deepseek";
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
