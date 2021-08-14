package com.kms.katalon.core.db;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.Properties;

import org.apache.commons.dbutils.DbUtils;
import org.apache.commons.lang3.StringUtils;

import com.kms.katalon.core.constants.StringConstants;
import com.kms.katalon.core.exception.DriverNotFoundException;
import com.kms.katalon.core.logging.KeywordLogger;
import com.kms.katalon.core.testdata.TestDataInfo;

/**
 * Database Connection
 */
public class DatabaseConnection {

	private final KeywordLogger logger = KeywordLogger.getInstance(DatabaseConnection.class);

	private static final String PW_PROPERTY = "password";

	private static final String USER_PROPERTY = "user";

	private String connectionUrl;

	private Connection connection;

	private String user;

	private String password;

	private String driverClassName;

	private TestDataInfo dbDataInfo;

	/**
	 * Database Connection with user and password included in URL
	 * 
	 * @param connectionUrl
	 *            JDBC connection URL
	 */
	public DatabaseConnection(String connectionUrl) {
		this.connectionUrl = connectionUrl;
	}
	
	public DatabaseConnection(String connectionUrl, String driverClassName) {
		this.connectionUrl = connectionUrl;
		this.driverClassName = driverClassName;
	}

	/**
	 * Database Connection
	 * 
	 * @param connectionUrl
	 *            JDBC connection URL
	 * @param user
	 *            the name of the user
	 * @param password
	 *            the plain text password
	 */
	public DatabaseConnection(String connectionUrl, String user, String password) {
		this(connectionUrl);
		this.user = user;
		this.password = password;
	}

	public DatabaseConnection(String connectionUrl, String user, String password, String driverClassName) {
		this(connectionUrl);
		this.user = user;
		this.password = password;
		this.driverClassName = driverClassName;
	}

	public String getUserName() {
		return this.user;
	}

	public String getPassword() {
		return this.password;
	}

	public String getDriverClassName() {
		return this.driverClassName;
	}

	public String getConnectionUrl() {
		return connectionUrl;
	}

	/**
	 * Obtain a connection using the given connection URL with fulfill
	 * properties (user and password should be included)
	 * 
	 * @return the obtained Connection
	 * @throws SQLException
	 *             in case of failure
	 */
	public Connection getConnection() throws SQLException {
		Properties properties = new Properties();
		// if user & password are specified, they will override the user and
		// password in URL if found
		if (user != null) {
			properties.setProperty(USER_PROPERTY, user);
		}
		if (password != null) {
			properties.setProperty(PW_PROPERTY, password);
		}
		return getConnection(properties);
	}

    /**
     * Obtain a Connection using the given properties.
     *
     * @param properties
     * the connection properties
     * @return the obtained Connection
     * @throws SQLException
     * in case of failure
     * @see java.sql.DriverManager#getConnection(String, java.util.Properties)
     */
    private Connection getConnection(Properties properties) throws SQLException {
        if (isAlive()) {
            return connection;
        }

        String loadedDriverClassName = loadSuitableDatabaseDriver();

        if (StringUtils.isNotEmpty(loadedDriverClassName)) {
            try {
                connection = ((Driver) Class
                        .forName(loadedDriverClassName, true, Thread.currentThread().getContextClassLoader())
                        .newInstance()).connect(connectionUrl, properties);
            } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
                connection = DriverManager.getConnection(connectionUrl, properties);
            }
        } else {
            connection = DriverManager.getConnection(connectionUrl, properties);
        }
        // Disable auto commit
        connection.setAutoCommit(false);
        // Enable read-only
        connection.setReadOnly(true);

        logNewConnection();

        return connection;
    }

    private void logNewConnection() {
        dbDataInfo = newDBDataInfo(connection);
        logger.logRunData(dbDataInfo.getKey(), dbDataInfo.getInfo());
    }

    /**
     * This is a fallback function to load suitable supported database driver.
     * <br>
     * Since version 4.0, JDBC Drivers will be detected and loaded by connection
     * URL.
     */
    private String loadSuitableDatabaseDriver() {
        try {
            if (StringUtils.isNotEmpty(driverClassName)) {
                if (driverClassName.equals("com.mysql.cj.jdbc.Driver")
                        && StringUtils.startsWith(connectionUrl, "jdbc:mysql")) {
                    try {
                        return loadDriverIntoClassPath("com.mysql.cj.jdbc.Driver");
                    } catch (ClassNotFoundException e) {
                        throw new DriverNotFoundException(
                                MessageFormat.format(StringConstants.KRE_MSG_DRIVER_NOT_FOUND, connectionUrl));
                    }
                }
                Class.forName(driverClassName, true, Thread.currentThread().getContextClassLoader());
                return driverClassName;
            } else {
                if (StringUtils.startsWith(connectionUrl, "jdbc:mysql")) {
                    try {
                        return loadDriverIntoClassPath("com.mysql.cj.jdbc.Driver");
                    } catch (ClassNotFoundException e) {
                        throw new DriverNotFoundException(
                                MessageFormat.format(StringConstants.KRE_MSG_DRIVER_NOT_FOUND, connectionUrl));
                    }
                }

                if (StringUtils.startsWith(connectionUrl, "jdbc:sqlserver")) {
                    return loadDriverIntoClassPath("com.microsoft.sqlserver.jdbc.SQLServerDriver");
                }

                if (StringUtils.startsWith(connectionUrl, "jdbc:oracle")) {
                    return loadDriverIntoClassPath("oracle.jdbc.OracleDriver");
                }

                if (StringUtils.startsWith(connectionUrl, "jdbc:postgresql")) {
                    return loadDriverIntoClassPath("org.postgresql.Driver");
                }
            }

        } catch (ClassNotFoundException e) {
            // do nothing
        }
        return "";
    }

    private String loadDriverIntoClassPath(String driverName) throws ClassNotFoundException {
        Class.forName(driverName, true, Thread.currentThread().getContextClassLoader());
        return driverName;
    }

	public boolean isAlive() {
		try {
			return connection != null && !connection.isClosed();
		} catch (SQLException e) {
			return false;
		}
	}

	/**
	 * Close connection, avoid closing if null and hide any SQLExceptions that
	 * occur.
	 */
	public void close() {
		DbUtils.closeQuietly(connection);
	}

	public TestDataInfo getDBDataInfo() {
		return dbDataInfo;
	}

	public static TestDataInfo newDBDataInfo(Connection connection) {
		if (connection == null) {
			return null;
		}

		try {
			DatabaseMetaData connectionMetaData = connection.getMetaData();

			return new TestDataInfo(StringConstants.XML_LOG_DB_SERVER_INFO,
					connectionMetaData.getDatabaseProductName() + " " + connectionMetaData.getDatabaseProductVersion());
		} catch (SQLException e) {
			return null;
		}
	}
}
