package ru.alekkol.jdbc;

import org.apache.commons.dbcp2.BasicDataSource;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class PoolVsNotPoolComparison {
    private static DataSource createDataSourceWithPool() {
        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl("jdbc:postgresql://localhost/testdb");
        dataSource.setUsername("postgres");
        dataSource.setPassword("postgres");
        dataSource.setInitialSize(1);
        dataSource.setMinIdle(1);
        dataSource.setMaxIdle(1);
        dataSource.setMaxTotal(1);
        dataSource.setTestOnBorrow(false);
        dataSource.setTestOnReturn(false);
        dataSource.setTestWhileIdle(false);
        dataSource.setTestOnReturn(false);
        return dataSource;
    }

    private static DataSource createDataSourceWithoutPool() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl("jdbc:postgresql://localhost/testdb");
        dataSource.setUser("postgres");
        dataSource.setPassword("postgres");
        return dataSource;
    }

    public static void main(String[] args) throws SQLException {
        final Map<String, DataSource> dataSourceMap = Map.of(
                "with pool", createDataSourceWithPool(),
                "without pool", createDataSourceWithoutPool());
        for (Map.Entry<String, DataSource> nameToDataSource : dataSourceMap.entrySet()) {
            for (int i = 0; i < 10; i++) {
                final long start = System.nanoTime();
                try (Connection connection = nameToDataSource.getValue().getConnection();
                     PreparedStatement preparedStatement = connection.prepareStatement("select now();")) {
                    preparedStatement.execute();
                }
                System.out.println((i + 1) + " "
                        + nameToDataSource.getKey() + " "
                        + TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - start));
            }
        }
    }
}
