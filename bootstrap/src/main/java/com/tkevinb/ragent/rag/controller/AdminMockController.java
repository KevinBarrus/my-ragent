package com.tkevinb.ragent.rag.controller;

import com.tkevinb.ragent.framework.convention.Result;
import com.tkevinb.ragent.framework.web.Results;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理后台 mock 控制器 — 所有管理接口返回空数据
 * <p>
 * 在管理后台功能未实现期间，防止前端报错。
 */
@RestController
public class AdminMockController {

    @GetMapping("/admin/dashboard/overview")
    public Result<Map<String, Object>> overview() {
        return Results.success(Map.of());
    }

    @GetMapping("/admin/dashboard/performance")
    public Result<Map<String, Object>> performance() {
        return Results.success(Map.of());
    }

    @GetMapping("/admin/dashboard/trends")
    public Result<Map<String, Object>> trends() {
        return Results.success(Map.of());
    }

    @GetMapping("/rag/settings")
    public Result<Map<String, Object>> settings() {
        return Results.success(Map.of());
    }

    @GetMapping("/rag/sample-questions")
    public Result<List<Map<String, Object>>> sampleQuestions() {
        return Results.success(List.of());
    }

    @GetMapping("/sample-questions")
    public Result<Map<String, Object>> sampleQuestionsPage() {
        return Results.success(Map.of("records", List.of(), "total", 0));
    }

    @PostMapping("/sample-questions")
    public Result<String> createSample() { return Results.success("mock"); }

    @PutMapping("/sample-questions/{id}")
    public Result<Void> updateSample(@PathVariable String id) { return Results.success(); }

    @DeleteMapping("/sample-questions/{id}")
    public Result<Void> deleteSample(@PathVariable String id) { return Results.success(); }

    @GetMapping("/knowledge-base/chunk-strategies")
    public Result<List<Map<String, Object>>> chunkStrategies() { return Results.success(List.of()); }

    @GetMapping("/knowledge-base")
    public Result<Map<String, Object>> knowledgeBase() {
        return Results.success(Map.of("records", List.of(), "total", 0));
    }

    @GetMapping("/knowledge-base/{id}")
    public Result<Map<String, Object>> knowledgeBaseOne(@PathVariable String id) {
        return Results.success(Map.of("id", id));
    }

    @GetMapping("/knowledge-base/{kbId}/docs")
    public Result<Map<String, Object>> knowledgeDocs(@PathVariable String kbId) {
        return Results.success(Map.of("records", List.of(), "total", 0));
    }

    @GetMapping("/knowledge-base/docs/search")
    public Result<List<Map<String, Object>>> searchDocs() { return Results.success(List.of()); }
}
