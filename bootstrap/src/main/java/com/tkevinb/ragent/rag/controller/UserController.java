package com.tkevinb.ragent.rag.controller;

import com.tkevinb.ragent.framework.convention.Result;
import com.tkevinb.ragent.framework.web.Results;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 临时用户接口 — 兼容 ragent 前端
 */
@RestController
public class UserController {

    @GetMapping("/user/me")
    public Result<Map<String, Object>> me() {
        return Results.success(Map.of(
                "userId", "2001523723396308993",
                "username", "admin",
                "role", "admin",
                "avatar", "https://static.deepseek.com/user-avatar/default.png"
        ));
    }
}
