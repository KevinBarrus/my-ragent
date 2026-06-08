package com.tkevinb.ragent.rag.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.tkevinb.ragent.framework.convention.Result;
import com.tkevinb.ragent.framework.web.Results;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 临时认证控制器 — 兼容 ragent 前端
 */
@RestController
public class AuthController {

    @PostMapping("/auth/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (!"admin".equals(username) || !"admin".equals(password)) {
            Result<Map<String, Object>> r = new Result<>();
            r.setCode("401");
            r.setMessage("用户名或密码错误");
            return r;
        }

        StpUtil.login("2001523723396308993");

        return Results.success(Map.of(
                "userId", "2001523723396308993",
                "username", username,
                "token", StpUtil.getTokenValue(),
                "role", "admin"
        ));
    }

    @PostMapping("/auth/logout")
    public Result<Void> logout() {
        StpUtil.logout();
        return Results.success();
    }
}
