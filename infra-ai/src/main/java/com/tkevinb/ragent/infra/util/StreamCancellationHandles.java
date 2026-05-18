package com.tkevinb.ragent.infra.util;

import com.tkevinb.ragent.infra.chat.StreamCancellationHandle;
import lombok.NoArgsConstructor;
import okhttp3.Call;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * StreamCancellationHandles 工具类
 * 用于构建常见的取消句柄，统一幂等取消语义
 */
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class StreamCancellationHandles {

    private static final StreamCancellationHandle NOOP = () -> {
    };

    public static StreamCancellationHandle noop() {
        return NOOP;
    }

    public static StreamCancellationHandle fromOkHttp(Call call, AtomicBoolean cancelled) {
        return new OkHttpCancellationHandle(call, cancelled);
    }

    private static final class OkHttpCancellationHandle implements StreamCancellationHandle {

        private final Call call;
        private final AtomicBoolean cancelled;
        private final AtomicBoolean once = new AtomicBoolean(false);

        private OkHttpCancellationHandle(Call call, AtomicBoolean cancelled) {
            this.call = call;
            this.cancelled = cancelled;
        }

        @Override
        public void cancel() {
            if (!once.compareAndSet(false, true)) {
                // 保证只执行一次：多次调用 cancel() 只有第一次生效
                return;
            }
            if (cancelled != null) {
                // 标记：已被取消
                // 通知业务层：流式解析循环读到这个标志就停止
                cancelled.set(true);
            }
            if (call != null) {
                // 断网，真正关闭 http 连接
                call.cancel();
            }
        }
    }
}

