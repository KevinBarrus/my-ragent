package com.tkevinb.ragent.rag.core.tool;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册中心
 * <p>
 * 自动发现所有 @Component 的 Tool 实现。
 * 新增工具只需写一个实现类 + 加 @Component，无需改动注册中心。
 */
@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new HashMap<>();

    public ToolRegistry(List<Tool> toolList) {
        for (Tool t : toolList) {
            tools.put(t.name(), t);
            log.info("工具注册: {}", t.name());
        }
    }

    public Tool get(String name) {
        return tools.get(name);
    }

    public Map<String, Tool> all() {
        return tools;
    }

    /** 返回 LLM 可读的工具列表描述 */
    public String getToolDescriptions() {
        StringBuilder sb = new StringBuilder();
        for (Tool t : tools.values()) {
            sb.append("- ").append(t.name()).append(": ").append(t.description()).append("\n");
            sb.append("  参数: ").append(t.parameterSchema()).append("\n");
        }
        return sb.toString();
    }
}
