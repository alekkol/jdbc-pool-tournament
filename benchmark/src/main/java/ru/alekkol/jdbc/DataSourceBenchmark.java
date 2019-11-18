package ru.alekkol.jdbc;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.commons.dbcp2.BasicDataSource;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import ru.alekkol.jdbc.pseudodriver.PseudoDataSource;
import ru.alekkol.jdbc.pseudodriver.PseudoDriver;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"SqlNoDataSourceInspection", "SqlDialectInspection"})
@State(Scope.Benchmark)
@Threads(20)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class DataSourceBenchmark {
    @Param({"hikari", "tomcat", "c3p0", "dbcp"})
    private String pool;
    @Param({"0", "1", "5"})
    private long executeDurationMillis;
    @Param({"1", "3", "5", "10", "20", "30"})
    private int poolSize;

    private DataSource dataSource;

    @Setup
    public void setUp() throws Exception {
        switch (pool) {
            case "baseline":
                this.dataSource = new PseudoDataSource(executeDurationMillis);
                break;
            case "hikari":
                HikariConfig config = new HikariConfig();
                config.setDriverClassName("ru.alekkol.jdbc.pseudodriver.PseudoDriver");
                config.setJdbcUrl("random");
                config.setMaximumPoolSize(poolSize);
                config.setMinimumIdle(poolSize);
                config.addDataSourceProperty(PseudoDriver.EXECUTE_DURATION_MILLIS, "" + executeDurationMillis);
                config.setConnectionTimeout(0); // infinite
                this.dataSource = new HikariDataSource(config);
                break;
            case "tomcat":
                final org.apache.tomcat.jdbc.pool.DataSource tomcatDataSource = new org.apache.tomcat.jdbc.pool.DataSource();
                tomcatDataSource.setDriverClassName("ru.alekkol.jdbc.pseudodriver.PseudoDriver");
                tomcatDataSource.setUrl("random");
                tomcatDataSource.setInitialSize(poolSize);
                tomcatDataSource.setMaxActive(poolSize);
                tomcatDataSource.setMaxIdle(poolSize);
                tomcatDataSource.setMinIdle(poolSize);
                tomcatDataSource.setTestOnBorrow(false);
                tomcatDataSource.setTestOnConnect(false);
                tomcatDataSource.setTestOnReturn(false);
                tomcatDataSource.setTestWhileIdle(false);
                tomcatDataSource.getDbProperties().put(PseudoDriver.EXECUTE_DURATION_MILLIS, "" + executeDurationMillis);
                this.dataSource = tomcatDataSource;
                break;
            case "c3p0":
                final ComboPooledDataSource c3p0DataSource = new ComboPooledDataSource();
                c3p0DataSource.setDriverClass("ru.alekkol.jdbc.pseudodriver.PseudoDriver");
                c3p0DataSource.setForceUseNamedDriverClass(true);
                c3p0DataSource.setJdbcUrl("random");
                c3p0DataSource.setInitialPoolSize(poolSize);
                c3p0DataSource.setMinPoolSize(poolSize);
                c3p0DataSource.setMaxPoolSize(poolSize);
                c3p0DataSource.setTestConnectionOnCheckin(false);
                c3p0DataSource.setTestConnectionOnCheckout(false);
                final Properties properties = new Properties();
                properties.put(PseudoDriver.EXECUTE_DURATION_MILLIS, "" + executeDurationMillis);
                c3p0DataSource.setProperties(properties);
                this.dataSource = c3p0DataSource;
                break;
            case "dbcp":
                BasicDataSource dbcpDataSource = new BasicDataSource();
                dbcpDataSource.setDriverClassName("ru.alekkol.jdbc.pseudodriver.PseudoDriver");
                dbcpDataSource.setUrl("random");
                dbcpDataSource.setInitialSize(poolSize);
                dbcpDataSource.setMinIdle(poolSize);
                dbcpDataSource.setMaxIdle(poolSize);
                dbcpDataSource.setMaxTotal(poolSize);
                dbcpDataSource.setTestOnBorrow(false);
                dbcpDataSource.setTestOnReturn(false);
                dbcpDataSource.setTestWhileIdle(false);
                dbcpDataSource.setTestOnReturn(false);
                dbcpDataSource.addConnectionProperty(PseudoDriver.EXECUTE_DURATION_MILLIS, "" + executeDurationMillis);
                this.dataSource = dbcpDataSource;
                break;
            default:
                throw new UnsupportedOperationException("invalid pool: " + pool);
        }
    }


    @Benchmark
    public boolean statement() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement("blabla")) {
            return preparedStatement.execute();
        }
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(DataSourceBenchmark.class.getSimpleName())
                .forks(1)
                .warmupIterations(1)
                .warmupTime(TimeValue.seconds(10))
                .measurementIterations(5)
                .measurementTime(TimeValue.seconds(10))
                //.param("pool", "hikari")
                //.param("executeDurationMillis", "5")
                //.param("poolSize", "3")
                //.addProfiler(StackProfiler.class, "lines=20")
                //.addProfiler(GCProfiler.class)
                .build();

        new Runner(opt).run();
    }
}
