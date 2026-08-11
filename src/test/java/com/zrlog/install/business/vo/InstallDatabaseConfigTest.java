package com.zrlog.install.business.vo;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InstallDatabaseConfigTest {

    @Test
    public void shouldIdentifyOnlyLocalSqliteConfigurations() {
        InstallDatabaseConfig sqlite = new InstallDatabaseConfig();
        sqlite.setDbType("sqlite");
        assertTrue(sqlite.isLocalSqlite());

        InstallDatabaseConfig webApi = new InstallDatabaseConfig();
        webApi.setDbType("webapi");
        assertFalse(webApi.isLocalSqlite());

        InstallDatabaseConfig sqliteUrl = new InstallDatabaseConfig();
        sqliteUrl.setJdbcUrl("jdbc:sqlite:/tmp/zrlog.db");
        assertTrue(sqliteUrl.isLocalSqlite());

        InstallDatabaseConfig mysql = new InstallDatabaseConfig();
        mysql.setDbType("mysql");
        mysql.setJdbcUrl("jdbc:mysql://localhost/zrlog");
        assertFalse(mysql.isLocalSqlite());
    }
}
