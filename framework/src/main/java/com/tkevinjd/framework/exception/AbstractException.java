package com.tkevinjd.framework.exception;

import com.tkevinjd.framework.errorMessage.ErrorMessage;
import lombok.Getter;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 将项目进行中可能出现的三种异常：客户端异常、服务端异常、远程服务调用异常 进行抽象
 */
@Getter
public abstract class AbstractException extends RuntimeException {

    public final String errorCode;

    public final String errorMessage;

    public AbstractException(String message, Throwable throwable, ErrorMessage errorMsg) {
        super(message, throwable);
        this.errorCode = errorMsg.code();
        this.errorMessage = Optional.ofNullable(StringUtils.hasLength(message)? message:null)
                .orElse(errorMsg.message());
    }

    /**
     * 自定义错误信息显示格式，便于调试
     * 如果后续三个子类有自己的特殊需求，就在对应的类中重写即可
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "{" +
                "code='" + errorCode + "'," +
                "message='" + errorMessage + "'" +
                '}';
    }
}
