package com.zrlog.install.business.service;

import com.zrlog.install.business.vo.InstallDatabaseConfig;
import com.zrlog.install.web.config.InstallConfig;

import java.io.File;
import java.util.Objects;

public final class LocalSqliteSupport {

    private LocalSqliteSupport() {
    }

    public static boolean isAvailable(InstallConfig installConfig) {
        return isSupportedRuntimeMode(InstallProbeService.getRuntimeMode(installConfig))
                && isDriverAvailable();
    }

    static boolean isSupportedRuntimeMode(String runtimeMode) {
        return Objects.equals(runtimeMode, "zip") || Objects.equals(runtimeMode, "native");
    }

    private static boolean isDriverAvailable() {
        try {
            Class.forName("org.sqlite.JDBC", false, LocalSqliteSupport.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    public static InstallDatabaseConfig createDatabaseConfig(InstallConfig installConfig) {
        File configParent = installConfig.getDbPropertiesFile().getAbsoluteFile().getParentFile();
        if (configParent == null) {
            throw new IllegalStateException("Missing database configuration directory");
        }
        File databaseFile = new File(configParent, "zrlog.db");
        InstallDatabaseConfig dbConn = new InstallDatabaseConfig();
        dbConn.setUser("");
        dbConn.setPassword("");
        dbConn.setDbType("sqlite");
        dbConn.setDbName("zrlog");
        dbConn.setDriverClass("org.sqlite.JDBC");
        dbConn.setJdbcUrl("jdbc:sqlite:" + databaseFile.toPath().toAbsolutePath().normalize()
                + "?journal_mode=WAL&busy_timeout=10000&foreign_keys=on&synchronous=NORMAL"
                + "&date_class=TEXT&date_string_format=yyyy-MM-dd HH:mm:ss");
        return dbConn;
    }
}
