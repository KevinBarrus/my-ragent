package com.tkevinjd.framework.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前登录用户的上下文
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginUser {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 角色（admin/user）
     */
    private String role;

    /**
     * 头像
     */
    private String avatar;
}
