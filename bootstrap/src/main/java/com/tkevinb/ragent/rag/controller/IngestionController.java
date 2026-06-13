package com.tkevinb.ragent.rag.controller;

import com.tkevinb.ragent.framework.convention.Result;
import com.tkevinb.ragent.framework.web.Results;
import com.tkevinb.ragent.rag.ingestion.IngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文档入库控制器
 */
@RestController
@RequestMapping("/ingestion")
@RequiredArgsConstructor
public class IngestionController {

    private final IngestionService ingestionService;

    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file,
                                  @RequestParam(defaultValue = "1") long kbId) throws IOException {
        if (file.isEmpty()) {
            Result<String> r = new Result<>();
            r.setCode("400");
            r.setMessage("文件为空");
            return r;
        }
        ingestionService.ingest(file.getBytes(), file.getOriginalFilename(), kbId);
        return Results.success("入库成功，文件名: " + file.getOriginalFilename());
    }
}
