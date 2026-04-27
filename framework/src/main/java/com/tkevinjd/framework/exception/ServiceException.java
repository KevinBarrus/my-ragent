package com.tkevinjd.framework.exception;

import com.tkevinjd.framework.errorMessage.BaseErrorCode;
import com.tkevinjd.framework.errorMessage.ErrorMessage;

/**
 * 服务端运行异常
 */
public class ServiceException extends AbstractException {

    public ServiceException(ErrorMessage errorMsg) {
        this(null, null, errorMsg);
    }

    public ServiceException(String message) {
        this(message, null, BaseErrorCode.SERVICE_ERROR);
    }

    public ServiceException(String message, ErrorMessage errorMsg) {
        this(message, null, errorMsg);
    }

    public ServiceException(String message, Throwable throwable, ErrorMessage errorMsg) {
        super(message, throwable, errorMsg);
    }

}
