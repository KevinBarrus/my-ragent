package com.tkevinb.ragent.rag.core.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 天气查询工具 — 模拟版
 * <p>
 * 后续可替换为真实天气 API（如 Tavily 或其他天气服务）。
 * 实现 Tool 接口 + @Component 即可自动注册。
 */
@Slf4j
@Component
public class WeatherTool implements Tool {

    private static final Map<String, String> MOCK_DATA = Map.of(
            "北京", "北京：晴天，25°C，东北风2级，湿度45%，空气质量良",
            "上海", "上海：多云，28°C，东南风3级，湿度65%，空气质量轻度污染",
            "广州", "广州：阵雨，30°C，南风2级，湿度80%，空气质量良",
            "深圳", "深圳：阴天，29°C，西南风2级，湿度75%，空气质量良"
    );

    @Override
    public String name() { return "get_weather"; }

    @Override
    public String description() { return "查询指定城市的实时天气信息。输入城市名，返回温度、天气状况和空气质量。"; }

    @Override
    public String parameterSchema() { return "{\"city\": \"城市名称（如：北京、上海）\"}"; }

    @Override
    public String execute(Map<String, Object> params) {
        String city = params != null ? (String) params.get("city") : null;
        if (city == null || city.isBlank()) return "请指定城市名称";
        return MOCK_DATA.getOrDefault(city, city + "：暂无天气数据，请确认城市名称");
    }
}
