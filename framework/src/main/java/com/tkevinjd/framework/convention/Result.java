package com.tkevinjd.framework.convention;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 全局统一返回结果对象
 * 用于规范化所有 API 接口的返回格式，确保前后端交互的一致性
 * 所有接口返回都应使用此对象包装，避免不同开发人员定义不一致的返回结构
 */
@Data
@Accessors(chain = true) //setter返回this，允许setter的链式调用
public class Result<T> implements Serializable {

    //如果不序列化版本号，那么一旦类发生变动，JVM就会生成新的序列化版本号，则会导致序列化前后的类不兼容，报错 InvalidClassException
    @Serial
    private static final long serialVersionUID = 5679018624309023727L;

    /**
     * 成功状态码
     * 当接口请求成功时，返回此状态码
     */
    public static final String SUCCESS_CODE = "0";

    /**
     * 状态码
     * 标识请求的处理结果，"0" 表示成功，其他值表示各类错误或异常情况
     */
    private String code;

    /**
     * 响应消息
     * 对本次请求结果的文字描述，成功时可为成功提示，失败时为错误原因说明
     */
    private String message;

    /**
     * 响应数据
     * 接口返回的业务数据，类型由泛型 T 指定。请求失败时可能为 null
     */
    private T data;

    /**
     * 请求追踪 ID
     * 用于链路追踪和问题排查，每个请求具有唯一的标识符
     */
    private String requestId;

    /**
     * 判断请求是否成功
     */
    public boolean isSuccess() {
        return SUCCESS_CODE.equals(code);
    }
}
