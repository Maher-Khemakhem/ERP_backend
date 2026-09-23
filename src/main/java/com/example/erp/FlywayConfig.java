package com.example.erp;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.flyway.enabled", havingValue = "true", matchIfMissing = true)
public class FlywayConfig {

    @Bean(name = "flyway", initMethod = "migrate")
    Flyway flyway(DataSource dataSource) throws SQLException {
        createIdentityTables(dataSource);
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("platform")
                .defaultSchema("platform")
                .createSchemas(true)
                .locations("classpath:db/migration")
                .load();
    }

    private void createIdentityTables(DataSource dataSource) throws SQLException {
        try (var connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS public.app_user (
                        id UUID PRIMARY KEY,
                        username VARCHAR(80) NOT NULL UNIQUE,
                        display_name VARCHAR(120) NOT NULL,
                        password_hash VARCHAR(255) NOT NULL,
                        enabled BOOLEAN NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL,
                        version BIGINT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS public.app_user_roles (
                        user_id UUID NOT NULL REFERENCES public.app_user(id),
                        role VARCHAR(40) NOT NULL,
                        PRIMARY KEY (user_id, role)
                    )
                    """);
        }
    }

    @Bean
    static BeanFactoryPostProcessor flywayBeforeJpa() {
        return new BeanFactoryPostProcessor() {
            @Override
            public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
                if (beanFactory instanceof BeanDefinitionRegistry registry
                        && registry.containsBeanDefinition("entityManagerFactory")) {
                    registry.getBeanDefinition("entityManagerFactory").setDependsOn("flyway");
                }
            }
        };
    }
}
