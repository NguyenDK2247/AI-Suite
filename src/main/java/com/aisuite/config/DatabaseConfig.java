package com.aisuite.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Configuration
public class DatabaseConfig {

    // Enable WAL mode and foreign keys after DataSource is ready
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) throws Exception {
        try (Connection conn = dataSource.getConnection();
                Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA foreign_keys=ON");
        }
        return new JdbcTemplate(dataSource);
    }
}
