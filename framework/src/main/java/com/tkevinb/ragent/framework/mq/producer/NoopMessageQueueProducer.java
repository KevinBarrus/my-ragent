package com.tkevinb.ragent.framework.mq.producer;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * Noop 消息队列生产者（MVP 占位）
 * <p>
 * 当 RocketMQ 未配置时使用，仅打印日志，不做实际发送。
 * 下午引入 RocketMQ 后，由 RocketMQ 的实现类自动覆盖。
 */
@Slf4j
@Component
@org.springframework.context.annotation.Primary
public class NoopMessageQueueProducer implements MessageQueueProducer {

    @Override
    public SendResult send(String topic, String keys, String bizDesc, Object body) {
        log.info("[NoopMQ] 发送消息 - topic: {}, keys: {}, bizDesc: {}, body: {}", topic, keys, bizDesc, body);
        SendResult result = new SendResult();
        result.setSendStatus(SendStatus.SEND_OK);
        result.setMsgId("noop-" + System.currentTimeMillis());
        return result;
    }

    @Override
    public void sendInTransaction(String topic, String keys, String bizDesc, Object body,
                                   Consumer<Object> localTransaction) {
        log.info("[NoopMQ] 发送事务消息 - topic: {}, keys: {}, bizDesc: {}", topic, keys, bizDesc);
        try {
            localTransaction.accept(body);
        } catch (Exception e) {
            log.error("[NoopMQ] 本地事务执行失败", e);
            throw e;
        }
    }
}
