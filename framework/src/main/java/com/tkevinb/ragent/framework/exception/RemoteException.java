package com.tkevinb.ragent.framework.exception;

import com.tkevinb.ragent.framework.errorMessage.BaseErrorCode;
import com.tkevinb.ragent.framework.errorMessage.ErrorMessage;

public class RemoteException extends AbstractException {

    public RemoteException(ErrorMessage errorMsg) {
        this(null, null, errorMsg);
    }

    public RemoteException(String message) {
        this(message, null, BaseErrorCode.REMOTE_ERROR);
    }

    public RemoteException(String message, ErrorMessage errorMsg) {
        this(message, null, errorMsg);
    }

    public RemoteException(String message, Throwable throwable, ErrorMessage errorMsg) {
        super(message, throwable, errorMsg);
    }

}
