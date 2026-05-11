package com.cinetrack.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.Map;

// Read-replica routing for Spring Boot (chapter 22.7). @Transactional(readOnly=true)
// methods are routed to the replica; everything else hits the primary. The
// LazyConnectionDataSourceProxy wrap is critical — without it, Spring acquires
// a connection at transaction start before the routing key is available.
@Configuration
@ConditionalOnProperty(name = "cinetrack.read-replica.enabled", havingValue = "true")
public class DataSourceConfig {

    enum DataSourceTarget { PRIMARY, REPLICA }

    @Bean
    @ConfigurationProperties("spring.datasource")
    public HikariDataSource primaryDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    @Bean
    @ConfigurationProperties("cinetrack.read-replica.datasource")
    public HikariDataSource replicaDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    @Bean
    @Primary
    public DataSource routingDataSource(HikariDataSource primaryDataSource,
                                        HikariDataSource replicaDataSource) {
        var routing = new AbstractRoutingDataSource() {
            @Override
            protected Object determineCurrentLookupKey() {
                return TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                        ? DataSourceTarget.REPLICA
                        : DataSourceTarget.PRIMARY;
            }
        };
        routing.setTargetDataSources(Map.of(
                DataSourceTarget.PRIMARY, primaryDataSource,
                DataSourceTarget.REPLICA, replicaDataSource));
        routing.setDefaultTargetDataSource(primaryDataSource);
        routing.afterPropertiesSet();

        // LazyConnectionDataSourceProxy defers acquiring a connection until the
        // first JDBC call. Without it, Spring grabs the connection at @Transactional
        // entry — which happens before isCurrentTransactionReadOnly() is set.
        return new LazyConnectionDataSourceProxy(routing);
    }
}
