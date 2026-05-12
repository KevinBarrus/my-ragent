package com.tkevinb.ragent.framework.database;

import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.util.Date;
/**
 * MyBatis-Plus 源数据自动填充类
 * 可以自动填充指定的字段，在需要自动填充的字段加上@TableField(fill = FieldFill.INSERT_UPDATE)注解即可
 */

@Component
public class MyMetaDatabaseHandler implements MetaObjectHandler{

    @Override
    public void insertFill(MetaObject metaObject) {
        strictInsertFill(metaObject, "createTime", Date::new, Date.class);
        strictInsertFill(metaObject, "updateTime", Date::new, Date.class);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateTime", Date::new, Date.class);
    }
}
