package com.tkevinb.ragent.framework.exception;

import com.tkevinb.ragent.framework.errorMessage.BaseErrorCode;
import com.tkevinb.ragent.framework.errorMessage.ErrorMessage;

/**
 * 客户端运行异常
 */
public class ClientException extends AbstractException {

    public ClientException(ErrorMessage errorMsg) {
        this(null, null, errorMsg);
    }

    public ClientException(String message) {
        this(message, null, BaseErrorCode.CLIENT_ERROR);
    }

    public ClientException(String message, ErrorMessage errorMsg) {
        this(message, null, errorMsg);
    }

    // 无论是什么情况，都委托给这个构造函数，这样就避免了重复的代码
    public ClientException(String message, Throwable throwable, ErrorMessage errorMsg) {
        super(message, throwable, errorMsg);
    }

}
