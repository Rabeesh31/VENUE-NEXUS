package com.venuenexus.audit.adapter;

import com.venuenexus.audit.port.AuditLogStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Spring configuration providing deterministic AuditLogStore bean resolution.
 * Uses the JDBC audit store whenever the application has a configured datasource.
 * In-memory stores are used explicitly by unit tests, not by the running application.
 */
@Configuration
public class AuditStoreConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
    public AuditLogStore jdbcAuditLogStore(DataSource dataSource) {
        return new JdbcAuditLogStore(new JdbcTemplate(dataSource));
    }

    @Bean
    @ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "false")
    public AuditLogStore inMemoryAuditLogStore() {
        return new InMemoryAuditLogStore();
    }
}
