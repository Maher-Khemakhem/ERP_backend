package com.example.erp;

import com.example.erp.shared.TenantContext;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.AbstractDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
class TenantDataSourceConfig {

    @Bean
    static BeanPostProcessor tenantDataSourcePostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if ("dataSource".equals(beanName) && bean instanceof DataSource dataSource) {
                    return new TenantAwareDataSource(dataSource);
                }
                return bean;
            }
        };
    }

    private static final class TenantAwareDataSource extends AbstractDataSource {
        private final DataSource delegate;

        private TenantAwareDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return configure(delegate.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return configure(delegate.getConnection(username, password));
        }

        private Connection configure(Connection connection) throws SQLException {
            UUID storeId = TenantContext.currentStore();
            if (storeId == null) {
                resetStore(connection);
            } else {
                setStore(connection, storeId);
            }
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    new ResetOnCloseHandler(connection));
        }

        private static void setStore(Connection connection, UUID storeId) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SELECT set_config('app.current_store_id', '" + storeId + "', false)");
            }
        }

        private static void resetStore(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                statement.execute("RESET app.current_store_id");
            }
        }

        private static final class ResetOnCloseHandler implements InvocationHandler {
            private final Connection delegate;

            private ResetOnCloseHandler(Connection delegate) {
                this.delegate = delegate;
            }

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if ("close".equals(method.getName()) && method.getParameterCount() == 0) {
                    try {
                        resetStore(delegate);
                    } finally {
                        return method.invoke(delegate, args);
                    }
                }
                return method.invoke(delegate, args);
            }
        }
    }
}
