package com.tkevinjd.framework.errorMessage;

/**
 * 定义错误码接口
 */
public interface ErrorMessage {

    /**
     * 错误码
     */
    String code();

    /**
     * 错误信息
     */
    String message();
}
