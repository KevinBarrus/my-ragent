package com.tkevinb.ragent.rag.core.tool;

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
 * 密码重置工具 — 模拟业务系统密码重置
 * <p>
 * 实际生产中对接 HR 系统或 AD 域控。
 */
@Slf4j
@Component
public class ResetPasswordTool implements Tool {

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    @Override
    public String name() { return "reset_password"; }

    @Override
    public String description() { return "重置企业系统登录密码。输入员工工号或姓名，返回重置结果。"; }

    @Override
    public String parameterSchema() { return "{\"employeeId\": \"员工工号（如：EMP001）\"}"; }

    @Override
    public String execute(Map<String, Object> params) {
        String employeeId = params != null ? (String) params.get("employeeId") : null;
        if (employeeId == null || employeeId.isBlank()) {
            return "请提供员工工号";
        }
        // 模拟：实际对接 AD/HR 系统
        String tempPassword = "Temp@" + (int)(Math.random() * 10000);
        return String.format("已为员工 %s 生成临时密码：%s。请员工在登录后立即修改密码。", employeeId, tempPassword);
    }
}
