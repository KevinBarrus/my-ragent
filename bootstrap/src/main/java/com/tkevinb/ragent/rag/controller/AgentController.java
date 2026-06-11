package com.tkevinb.ragent.rag.controller;

import com.tkevinb.ragent.framework.convention.Result;
import com.tkevinb.ragent.framework.web.Results;
import com.tkevinb.ragent.rag.core.tool.ReActService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ReAct Agent 测试接口
 */
@RestController
@RequiredArgsConstructor
public class AgentController {

    private final ReActService agentService;

    @GetMapping("/agent/chat")
    public Result<String> chat(@RequestParam String question) {
        String answer = agentService.execute(question);
        return Results.success(answer);
    }
}
