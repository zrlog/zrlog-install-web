package com.zrlog.install.support;

import com.hibegin.common.dao.InMemoryDatabase;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

public enum TestDatabase {
    H2(InMemoryDatabase.H2_DRIVER_CLASS, "h2", "sa"),
    SQLITE("org.sqlite.JDBC", "sqlite", "");

    private final String driverClass;
    private final String dbType;
    private final String user;

    TestDatabase(String driverClass, String dbType, String user) {
        this.driverClass = driverClass;
        this.dbType = dbType;
        this.user = user;
    }

    public Configuration create(File root, String name) {
        String id = name + "_" + UUID.randomUUID();
        String jdbcUrl;
        if (this == H2) {
            jdbcUrl = InMemoryDatabase.h2JdbcUrl(id);
        } else {
            Path databaseFile = root.toPath().resolve(id + ".db").toAbsolutePath().normalize();
            jdbcUrl = "jdbc:sqlite:" + databaseFile
                    + "?journal_mode=WAL&busy_timeout=10000&foreign_keys=on&synchronous=NORMAL"
                    + "&date_class=TEXT&date_string_format=yyyy-MM-dd HH:mm:ss";
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put("driverClass", driverClass);
        values.put("jdbcUrl", jdbcUrl);
        values.put("user", user);
        values.put("password", "");
        values.put("dbType", dbType);
        values.put("dbName", "zrlog");
        if (this == H2) {
            values.put("dbHost", "localhost");
            values.put("dbPort", "0");
        }
        return new Configuration(values);
    }

    public String driverClass() {
        return driverClass;
    }

    public boolean isSqlite() {
        return this == SQLITE;
    }

    public static final class Configuration {

        private final Map<String, String> values;

        private Configuration(Map<String, String> values) {
            this.values = values;
        }

        public Map<String, String> asMap() {
            return new LinkedHashMap<>(values);
        }

        public Properties asProperties() {
            Properties properties = new Properties();
            properties.putAll(values);
            return properties;
        }

        public Connection openConnection() throws Exception {
            Class.forName(values.get("driverClass"));
            return DriverManager.getConnection(values.get("jdbcUrl"), values.get("user"), values.get("password"));
        }

        public void writeProperties(File file) throws Exception {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IllegalStateException("Can not create test database configuration directory");
            }
            try (OutputStream outputStream = Files.newOutputStream(file.toPath())) {
                asProperties().store(outputStream, "Test database configuration");
            }
        }
    }
}
