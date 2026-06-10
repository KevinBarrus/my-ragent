package com.tkevinb.ragent.framework.trace;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * RagTrace AOP 切面
 * <p>
 * 拦截 @RagTraceNode 方法，输出调用链耗时。
 */
@Slf4j
@Aspect
@Component
public class RagTraceAspect {

    private final ThreadLocal<AtomicInteger> depth = ThreadLocal.withInitial(AtomicInteger::new);

    @Around("@annotation(traceNode)")
    public Object trace(ProceedingJoinPoint pjp, RagTraceNode traceNode) throws Throwable {
        int d = depth.get().incrementAndGet();
        long start = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            log.info("[Trace] {}{} | {}ms",
                    "  ".repeat(Math.max(0, d - 1)), traceNode.name(),
                    System.currentTimeMillis() - start);
            return result;
        } catch (Throwable t) {
            log.warn("[Trace] {}{} | {}ms | ERROR: {}",
                    "  ".repeat(Math.max(0, d - 1)), traceNode.name(),
                    System.currentTimeMillis() - start, t.getMessage());
            throw t;
        } finally {
            depth.get().decrementAndGet();
        }
    }
}
