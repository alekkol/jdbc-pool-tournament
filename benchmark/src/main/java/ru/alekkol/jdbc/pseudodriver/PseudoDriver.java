package ru.alekkol.jdbc.pseudodriver;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

public class PseudoDriver implements Driver {
    public static final String EXECUTE_DURATION_MILLIS = "executeDurationMillis";

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        final long executeDurationMillis = Long.parseLong(info.getProperty("executeDurationMillis"));
        return new PseudoConnection(executeDurationMillis);
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return true;
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion() {
        return 42;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public boolean jdbcCompliant() {
        return true;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return null;
    }
}
