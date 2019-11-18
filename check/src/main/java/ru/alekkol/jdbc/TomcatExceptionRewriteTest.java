package ru.alekkol.jdbc;

import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.postgresql.core.PGStream;
import org.postgresql.core.QueryExecutorBase;
import org.postgresql.jdbc.PgConnection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.Socket;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class TomcatExceptionRewriteTest {
    public static class MySqlException extends SQLException {
        private MySqlException(final String databaseName,
                               final String reason,
                               final String sqlState,
                               final SQLException cause,
                               final Socket socket) {
            super(
                    String.format("\n\t\t\t\t\tdatabase: [%s]\n\t\t\t\t\tconnection: [%s]\n\t\t\t\t\tstate: [%s]\n\t\t\t\t\treason: [%s]",
                            databaseName,
                            socket != null
                                    ? socket.getLocalAddress() + ":" + socket.getLocalPort() + " -> " + socket.getInetAddress() + ":" + socket.getPort()
                                    : "",
                            Objects.toString(sqlState, ""),
                            Objects.toString(reason, "")),
                    cause != null ? cause.getSQLState() : null,
                    cause
            );
        }

        public MySqlException(final String databaseName, final SQLException cause, final Socket socket) {
            this(databaseName, cause.getMessage(), cause.getSQLState(), cause, socket);
        }

        public MySqlException(final String databaseName, final SQLException cause) {
            this(databaseName, cause, null);
        }
    }

    public static class ExceptionHandlerInterceptor extends JdbcInterceptor {
        private String databaseName;
        private PooledConnection pooledConnection;

        @Override
        public void setProperties(final Map<String, PoolProperties.InterceptorProperty> properties) { //NOPMD
            super.setProperties(properties);
            databaseName = properties.get("databaseName").getValue();
        }

        @Override
        public void reset(final ConnectionPool connectionPool, final PooledConnection pooledConnection) {
            this.pooledConnection = pooledConnection;
        }

        private Optional<Socket> extractConnectionSocket() {
            try {
                final PgConnection connection = (PgConnection) pooledConnection.getConnection();
                final QueryExecutorBase queryExecutor = (QueryExecutorBase) connection.getQueryExecutor();
                final Field pgStreamField = QueryExecutorBase.class.getDeclaredField("pgStream");
                pgStreamField.setAccessible(true);
                final PGStream pgStream = (PGStream) pgStreamField.get(queryExecutor);
                Objects.requireNonNull(pgStream, "QueryExecutorBase.pgStream is null");
                return Optional.ofNullable(pgStream.getSocket());
            } catch (final Exception e) {
                return Optional.empty();
            }
        }

        @FunctionalInterface
        private interface InvocationFunction {
            Object invoke() throws Throwable;
        }

        private Object invokeAndWrapSqlException(final InvocationFunction invocationFunction) throws Throwable {
            try {
                return invocationFunction.invoke();
            } catch (final Throwable sourceException) {
                final SQLException sqlException;
                if (sourceException instanceof SQLException) {
                    sqlException = (SQLException) sourceException;
                } else if (sourceException.getCause() instanceof SQLException) {
                    sqlException = (SQLException) sourceException.getCause();
                } else {
                    sqlException = null;
                }

                if (sqlException == null) {
                    throw sourceException;
                } else {
                    throw extractConnectionSocket()
                            .map(socket -> new MySqlException(databaseName, sqlException, socket))
                            .orElseGet(() -> new MySqlException(databaseName, sqlException));
                }
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

    public static void main(String[] args) throws Exception {
        final DataSource dataSource = new DataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl("jdbc:postgresql://localhost/testdb");
        dataSource.setUsername("postgres");
        dataSource.setPassword("postgres");

        dataSource.setJdbcInterceptors(
                ExceptionHandlerInterceptor.class.getName()
                        + "(databaseName=very_important_database);");

        try (final Connection connection = dataSource.getConnection();
             final Statement statement = connection.createStatement()) {
            statement.execute("SELECT pg_terminate_backend(pg_backend_pid());");
        }
    }
}
