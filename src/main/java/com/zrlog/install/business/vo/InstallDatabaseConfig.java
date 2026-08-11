package com.zrlog.install.business.vo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public class InstallDatabaseConfig {

    private String user;
    private String password;
    private String dbType;
    private String dbHost;
    private String dbPort;
    private String dbName;
    private String driverClass;
    private String jdbcUrl;

    public boolean isLocalSqlite() {
        return "sqlite".equalsIgnoreCase(dbType)
                || "org.sqlite.JDBC".equals(driverClass)
                || startsWithIgnoreCase(jdbcUrl, "jdbc:sqlite:");
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value != null && value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    public static InstallDatabaseConfig from(Map<String, String> map) {
        InstallDatabaseConfig config = new InstallDatabaseConfig();
        if (map == null) {
            return config;
        }
        config.setUser(map.get("user"));
        config.setPassword(map.get("password"));
        config.setDbType(map.get("dbType"));
        config.setDbHost(map.get("dbHost"));
        config.setDbPort(map.get("dbPort"));
        config.setDbName(map.get("dbName"));
        config.setDriverClass(map.get("driverClass"));
        config.setJdbcUrl(map.get("jdbcUrl"));
        return config;
    }

    public Map<String, String> toMap() {
        Map<String, String> map = new LinkedHashMap<>();
        putIfPresent(map, "user", user);
        putIfPresent(map, "password", password);
        putIfPresent(map, "dbType", dbType);
        putIfPresent(map, "dbHost", dbHost);
        putIfPresent(map, "dbPort", dbPort);
        putIfPresent(map, "dbName", dbName);
        putIfPresent(map, "driverClass", driverClass);
        putIfPresent(map, "jdbcUrl", jdbcUrl);
        return map;
    }

    public Properties toProperties() {
        Properties properties = new Properties();
        properties.putAll(toMap());
        return properties;
    }

    private static void putIfPresent(Map<String, String> map, String key, String value) {
        if (Objects.nonNull(value)) {
            map.put(key, value);
        }
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDbType() {
        return dbType;
    }

    public void setDbType(String dbType) {
        this.dbType = dbType;
    }

    public String getDbHost() {
        return dbHost;
    }

    public void setDbHost(String dbHost) {
        this.dbHost = dbHost;
    }

    public String getDbPort() {
        return dbPort;
    }

    public void setDbPort(String dbPort) {
        this.dbPort = dbPort;
    }

    public String getDbName() {
        return dbName;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    public String getDriverClass() {
        return driverClass;
    }

    public void setDriverClass(String driverClass) {
        this.driverClass = driverClass;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }
}
