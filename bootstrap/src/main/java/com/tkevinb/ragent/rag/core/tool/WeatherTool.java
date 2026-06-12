package com.tkevinb.ragent.rag.core.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 天气查询工具 — 真实数据版（wttr.in 免费 API）
 * <p>
 * 调用 wttr.in 获取实时天气数据，解析 JSON 返回。
 * 不需要 API Key。
 */
@Slf4j
@Component
public class WeatherTool implements Tool {

    private static final String URL_TEMPLATE = "https://wttr.in/%s?format=j1&lang=zh";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    @Override
    public String name() { return "get_weather"; }

    @Override
    public String description() { return "查询指定城市的实时天气信息。输入城市名，返回温度、天气状况和空气质量。"; }

    @Override
    public String parameterSchema() { return "{\"city\": \"城市名称（如：北京）\"}"; }

    @Override
    public String execute(Map<String, Object> params) {
        String city = params != null ? (String) params.get("city") : null;
        if (city == null || city.isBlank()) return "请指定城市名称";
        try {
            String url = String.format(URL_TEMPLATE, java.net.URLEncoder.encode(city, "UTF-8"));
            Request req = new Request.Builder().url(url).build();
            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful()) return city + "：天气查询失败";
                String body = resp.body() != null ? resp.body().string() : "";
                return parseWeather(city, body);
            }
        } catch (IOException e) {
            log.warn("天气查询失败: {}", e.getMessage());
            return city + "：天气查询失败，请稍后再试";
        } catch (Exception e) {
            return city + "：天气查询失败";
        }
    }

    private String parseWeather(String city, String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject current = root.getAsJsonArray("current_condition").get(0).getAsJsonObject();

            String temp = current.get("temp_C").getAsString();
            String feelsLike = current.get("FeelsLikeC").getAsString();
            String humidity = current.get("humidity").getAsString();
            String windSpeed = current.get("windspeedKmph").getAsString();
            String windDir = current.get("winddir16Point").getAsString();
            String desc = current.getAsJsonArray("lang_zh").get(0).getAsJsonObject().get("value").getAsString();

            return String.format("%s：%s，气温%s°C（体感%s°C），%s风%s级，湿度%s%%",
                    city, desc, temp, feelsLike, windDir, windSpeed, humidity);
        } catch (Exception e) {
            log.warn("天气数据解析失败: {}", e.getMessage());
            return city + "：天气数据解析失败";
        }
    }
}
