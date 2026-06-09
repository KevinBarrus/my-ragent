package com.tkevinb.ragent.infra.model;

import com.tkevinb.ragent.infra.chat.StreamCallBack;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 流式首包探测桥
 * <p>
 * 包装用户的 callback，监听第一个 token 的到来。
 * 首包到达 → signal SUCCESS
 * 超时 → signal TIMEOUT
 */
public class ProbeStreamBridge implements StreamCallBack {

    private final StreamCallBack delegate;
    private final AtomicBoolean firstPacketArrived = new AtomicBoolean(false);
    private final CountDownLatch firstPacketLatch = new CountDownLatch(1);

    public ProbeStreamBridge(StreamCallBack delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onContent(String chunk) {
        if (firstPacketArrived.compareAndSet(false, true)) {
            firstPacketLatch.countDown();
        }
        delegate.onContent(chunk);
    }

    @Override
    public void onThinking(String chunk) {
        if (firstPacketArrived.compareAndSet(false, true)) {
            firstPacketLatch.countDown();
        }
        delegate.onThinking(chunk);
    }

    @Override
    public void onComplete() {
        delegate.onComplete();
    }

    @Override
    public void onError(Throwable t) {
        delegate.onError(t);
    }

    /** 等待首包，返回结果 */
    public ProbeResult awaitFirstPacket(long timeoutMs) throws InterruptedException {
        boolean arrived = firstPacketLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
        return arrived ? ProbeResult.SUCCESS : ProbeResult.TIMEOUT;
    }

    public enum ProbeResult { SUCCESS, TIMEOUT }
}
