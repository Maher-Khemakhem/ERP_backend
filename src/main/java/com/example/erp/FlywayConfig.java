package com.example.erp;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class FlywayConfig {

    @Bean(name = "flyway", initMethod = "migrate")
    Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("platform")
                .defaultSchema("platform")
                .createSchemas(true)
                .locations("classpath:db/migration")
                .load();
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