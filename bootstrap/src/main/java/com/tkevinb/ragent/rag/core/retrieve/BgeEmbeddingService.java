package com.tkevinb.ragent.rag.core.retrieve;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * BGE Embedding 服务 — 硅基流动 bge-large-zh-v1.5
 */
@Slf4j
@Service
public class BgeEmbeddingService {

    private static final String EMBEDDING_URL = "https://api.siliconflow.cn/v1/embeddings";
    private static final String MODEL = "BAAI/bge-large-zh-v1.5";
    private static final int DIM = 1024;

    private final OkHttpClient client;
    private final String apiKey;

    public BgeEmbeddingService(@Value("${rag.embedding.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public float[] embed(String text) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("model", MODEL);
            JsonArray inputArr = new JsonArray();
            inputArr.add(text.length() > 500 ? text.substring(0, 500) : text);
            body.add("input", inputArr);

            Request req = new Request.Builder()
                    .url(EMBEDDING_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                    .build();

            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    log.warn("BGE embedding 失败: HTTP {} {}", resp.code(), resp.body() != null ? resp.body().string() : "");
                    return null;
                }
                JsonObject result = JsonParser.parseString(resp.body().string()).getAsJsonObject();
                JsonArray data = result.getAsJsonArray("data");
                JsonArray vec = data.get(0).getAsJsonObject().getAsJsonArray("embedding");
                float[] embedding = new float[vec.size()];
                for (int i = 0; i < vec.size(); i++) embedding[i] = vec.get(i).getAsFloat();
                return embedding;
            }
        } catch (IOException e) {
            log.warn("BGE embedding 调用失败: {}", e.getMessage());
            return null;
        }
    }


}
