package com.tkevinb.ragent.rag.core.retrieve;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tkevinb.ragent.framework.convention.RetrievedChunk;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Cross-Encoder Rerank 服务 — 硅基流动 bge-reranker-v2-m3
 * <p>
 * 对粗排后的候选 chunk 重新打分，问题和 chunk 一起喂给模型，判断更精准。
 */
@Slf4j
@Service
public class BgeRerankService {

    private static final String RERANK_URL = "https://api.siliconflow.cn/v1/rerank";
    private static final String MODEL = "BAAI/bge-reranker-v2-m3";

    private final OkHttpClient client;
    private final String apiKey;

    public BgeRerankService(@Value("${rag.embedding.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 对候选 chunk 重新打分，按新分数排序返回
     */
    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates) {
        if (candidates.isEmpty()) return candidates;

        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.addProperty("query", query.substring(0, Math.min(query.length(), 256)));
        JsonArray docs = new JsonArray();
        for (RetrievedChunk c : candidates) {
            String text = c.getText();
            if (text != null && text.length() > 400) text = text.substring(0, 400);
            docs.add(text != null ? text : "");
        }
        body.add("documents", docs);

        try {
            Request req = new Request.Builder()
                    .url(RERANK_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                    .build();

            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful()) return candidates;
                JsonObject result = JsonParser.parseString(resp.body().string()).getAsJsonObject();
                JsonArray results = result.getAsJsonArray("results");

                List<RetrievedChunk> reranked = new ArrayList<>();
                for (int i = 0; i < results.size(); i++) {
                    JsonObject r = results.get(i).getAsJsonObject();
                    int idx = r.get("index").getAsInt();
                    double score = r.get("relevance_score").getAsDouble();
                    RetrievedChunk c = candidates.get(idx);
                    c.setScore((float) score);
                    reranked.add(c);
                }
                reranked.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));
                log.debug("Rerank: {} 个候选重新打分, 最高分={}", reranked.size(),
                        reranked.isEmpty() ? 0 : String.format("%.2f", reranked.get(0).getScore()));
                return reranked;
            }
        } catch (IOException e) {
            log.warn("Rerank 调用失败，使用原始排序: {}", e.getMessage());
            return candidates;
        }
    }
}
