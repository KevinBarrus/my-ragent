package com.tkevinb.ragent.rag.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * PGVector 数据源配置（独立于 MySQL 主库）
 */
@Configuration
public class PgVectorConfig {

    @Value("${rag.vector.datasource.url}")
    private String url;

    @Value("${rag.vector.datasource.username:postgres}")
    private String username;

    @Value("${rag.vector.datasource.password:}")
    private String password;

    @Bean
    public DataSource vectorDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setMaximumPoolSize(3);
        ds.setPoolName("HikariPool-PGVector");
        return ds;
    }

    @Bean
    public JdbcTemplate vectorJdbcTemplate(@Qualifier("vectorDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }
}
