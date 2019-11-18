package ru.alekkol.jdbc;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.tomcat.jdbc.pool.DataSource;

import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import java.util.Properties;

public class JmxSupport {
    public static void main(String[] args) throws Exception {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.postgresql.Driver");
        config.setJdbcUrl("jdbc:postgresql://localhost/testdb");
        config.setUsername("postgres");
        config.setPassword("postgres");
        config.setMaximumPoolSize(50);
        config.setMinimumIdle(10);
        config.setPoolName("mypool");
        config.setRegisterMbeans(true);
        final HikariDataSource hikariDataSource = new HikariDataSource(config);

        final DataSource tomcatDataSource = new DataSource();
        tomcatDataSource.setDriverClassName("org.postgresql.Driver");
        tomcatDataSource.setUrl("jdbc:postgresql://localhost/testdb");
        tomcatDataSource.setUsername("postgres");
        tomcatDataSource.setPassword("postgres");
        tomcatDataSource.setInitialSize(10);
        tomcatDataSource.setMaxActive(50);
        tomcatDataSource.setMaxIdle(10);
        tomcatDataSource.setMinIdle(10);
        tomcatDataSource.setTestOnBorrow(false);
        tomcatDataSource.setTestOnConnect(false);
        tomcatDataSource.setTestOnReturn(false);
        tomcatDataSource.setTestWhileIdle(false);
        tomcatDataSource.setJmxEnabled(true);
        MBeanServerFactory.createMBeanServer().registerMBean(tomcatDataSource.getPool().getJmxPool(), new ObjectName("org.apache.tomcat.jdbc:type=mypool"));
        tomcatDataSource.getConnection();

        final ComboPooledDataSource c3p0DataSource = new ComboPooledDataSource();
        c3p0DataSource.setDriverClass("org.postgresql.Driver");
        c3p0DataSource.setJdbcUrl("jdbc:postgresql://localhost/testdb");
        c3p0DataSource.setUser("postgres");
        c3p0DataSource.setPassword("postgres");
        c3p0DataSource.setInitialPoolSize(10);
        c3p0DataSource.setMinPoolSize(10);
        c3p0DataSource.setMaxPoolSize(50);
        c3p0DataSource.setTestConnectionOnCheckin(false);
        c3p0DataSource.setTestConnectionOnCheckout(false);
        final Properties properties = new Properties();
        c3p0DataSource.setProperties(properties);

        final BasicDataSource dbcpDataSource = new BasicDataSource();
        dbcpDataSource.setDriverClassName("org.postgresql.Driver");
        dbcpDataSource.setUrl("jdbc:postgresql://localhost/testdb");
        dbcpDataSource.setUsername("postgres");
        dbcpDataSource.setPassword("postgres");
        dbcpDataSource.setInitialSize(10);
        dbcpDataSource.setMinIdle(10);
        dbcpDataSource.setMaxIdle(50);
        dbcpDataSource.setMaxTotal(50);
        dbcpDataSource.setTestOnBorrow(false);
        dbcpDataSource.setTestOnReturn(false);
        dbcpDataSource.setTestWhileIdle(false);
        dbcpDataSource.setTestOnReturn(false);
        dbcpDataSource.setJmxName("org.apache.commons.dbcp2:type=mypool");
        dbcpDataSource.getConnection();

        Thread.sleep(Long.MAX_VALUE);
    }
}
