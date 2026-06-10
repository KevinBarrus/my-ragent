package com.tkevinb.ragent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.tkevinb.ragent.rag.dao.mapper")
@org.springframework.context.annotation.EnableAspectJAutoProxy
public class RagentApplication {
    public static void main(String[] args) {
        SpringApplication.run(RagentApplication.class, args);
    }
}
