package ru.alekkol.jdbc;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;
import org.apache.tomcat.jdbc.pool.PooledConnection;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;


@SuppressWarnings({"SqlNoDataSourceInspection", "SqlDialectInspection", "DuplicatedCode"})
public class ConnectionRecoveryTest {
    private static DataSource createC3p0DataSource() {
        ComboPooledDataSource dataSource = new ComboPooledDataSource();
        dataSource.setJdbcUrl("jdbc:postgresql://localhost/testdb");
        dataSource.setUser("postgres");
        dataSource.setPassword("postgres");
        return dataSource;
    }

    private static DataSource createTomcatDataSource() {
        final org.apache.tomcat.jdbc.pool.DataSource dataSource = new org.apache.tomcat.jdbc.pool.DataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl("jdbc:postgresql://localhost/testdb");
        dataSource.setUsername("postgres");
        dataSource.setPassword("postgres");
        dataSource.setJdbcInterceptors(
                ConnectionRecoveryInterceptor.class.getName());
        return dataSource;
    }

    private static DataSource createHikariDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost/testdb");
        config.setUsername("postgres");
        config.setPassword("postgres");
        return new HikariDataSource(config);
    }

    private static DataSource createDbcpDataSource() {
        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setUrl("jdbc:postgresql://localhost/testdb");
        dataSource.setUsername("postgres");
        dataSource.setPassword("postgres");
        return dataSource;
    }

    public static void main(String[] args) throws Exception {
        DataSource dataSource = createC3p0DataSource();

        try (final Connection connection = dataSource.getConnection();
             final Statement statement = connection.createStatement()) {
            statement.execute("SELECT pg_terminate_backend(pg_backend_pid())");
        } catch (final SQLException e) {
            // expected
        }
        try (final Connection connection = dataSource.getConnection();
             final Statement statement = connection.createStatement()) {
            statement.execute("SELECT now()");
        }
    }

    public static class ConnectionRecoveryInterceptor extends JdbcInterceptor {
        private static final Set<String> statesToReconnectAfter = Set.of("57P01");

        private PooledConnection pooledConnection;

        @Override
        public void reset(final ConnectionPool connectionPool, final PooledConnection pooledConnection) {
            this.pooledConnection = pooledConnection;
        }

        @FunctionalInterface
        private interface InvocationFunction {
            Object invoke() throws Throwable;
        }

        private Object invokeAndWrapSqlException(final InvocationFunction invocationFunction) throws Throwable {
            try {
                return invocationFunction.invoke();
            } catch (InvocationTargetException e) {
                Throwable targetException = e.getTargetException();
                if (targetException instanceof SQLException) {
                    SQLException sqlException = (SQLException) targetException;
                    if (statesToReconnectAfter.contains(sqlException.getSQLState())) {
                        pooledConnection.reconnect();
                    }
                }
                throw targetException;
            }
        }

        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
            final Object invocationResult = invokeAndWrapSqlException(
                    () -> super.invoke(proxy, method, args)
            );

            if (invocationResult instanceof Statement) {
                return Proxy.newProxyInstance(
                        Statement.class.getClassLoader(),
                        new Class<?>[]{method.getReturnType()},
                        (statementProxy, statementMethod, statementArgs) -> invokeAndWrapSqlException(
                                () -> statementMethod.invoke(invocationResult, statementArgs)
                        )
                );
            } else {
                return invocationResult;
            }
        }
    }
}
