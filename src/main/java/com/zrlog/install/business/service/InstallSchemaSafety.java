package com.zrlog.install.business.service;

import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.install.business.vo.InstallDatabaseConfig;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

final class InstallSchemaSafety {

    private static final Set<String> INSTALL_TABLES = Set.of(
            "link", "lognav", "plugin", "tag", "type", "user", "user_passkey",
            "user_passkey_challenge", "log", "log_extension_index", "comment", "log_version", "website");
    private static final String SQLITE_TABLE_QUERY = "SELECT name FROM sqlite_master "
            + "WHERE type IN ('table','view') AND lower(name) IN ("
            + "'link','lognav','plugin','tag','type','user','user_passkey','user_passkey_challenge',"
            + "'log','log_extension_index','comment','log_version','website')";
    private static final Pattern DROP_STATEMENT = Pattern.compile(
            "(?is)^(?:\\s|--[^\\r\\n]*(?:\\r?\\n|$)|#[^\\r\\n]*(?:\\r?\\n|$)|/\\*.*?\\*/)*"
                    + "DROP(?:\\s|/\\*)");

    private InstallSchemaSafety() {
    }

    static boolean containsInstallTable(DataSourceWrapper dataSource,
                                        InstallDatabaseConfig databaseConfig) throws SQLException {
        return !findInstallTables(dataSource, databaseConfig).isEmpty();
    }

    static boolean containsCompleteInstallSchema(DataSourceWrapper dataSource,
                                                 InstallDatabaseConfig databaseConfig) throws SQLException {
        return findInstallTables(dataSource, databaseConfig).containsAll(INSTALL_TABLES);
    }

    static boolean containsCompleteInstallState(DataSourceWrapper dataSource,
                                                InstallDatabaseConfig databaseConfig) throws SQLException {
        if (!containsCompleteInstallSchema(dataSource, databaseConfig)) {
            return false;
        }
        try (Connection connection = dataSource.getConnection()) {
            return hasRows(connection, "SELECT COUNT(1) FROM `user` WHERE `userId` = 1", 1)
                    && hasRows(connection, "SELECT COUNT(1) FROM `website` WHERE `name` IN "
                    + "('zrlogSqlVersion','title','template','language')", 4)
                    && hasRows(connection, "SELECT COUNT(1) FROM `lognav`", 2)
                    && hasRows(connection, "SELECT COUNT(1) FROM `plugin`", 4)
                    && hasRows(connection, "SELECT COUNT(1) FROM `type` WHERE `typeId` = 1", 1)
                    && hasRows(connection, "SELECT COUNT(1) FROM `tag` WHERE `tagId` = 1", 1)
                    && hasRows(connection, "SELECT COUNT(1) FROM `log` WHERE `logId` = 1", 1);
        }
    }

    private static boolean hasRows(Connection connection, String sql, long minimum) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() && resultSet.getLong(1) >= minimum;
        }
    }

    private static Set<String> findInstallTables(DataSourceWrapper dataSource,
                                                 InstallDatabaseConfig databaseConfig) throws SQLException {
        if (dataSource.isWebApi() || databaseConfig.isLocalSqlite()) {
            return findSqliteInstallTables(dataSource);
        }
        Set<String> installedTables = new HashSet<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet tables = metadata.getTables(
                    connection.getCatalog(), null, "%", new String[]{"TABLE", "VIEW"})) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    if (tableName != null && INSTALL_TABLES.contains(tableName.toLowerCase(Locale.ROOT))) {
                        installedTables.add(tableName.toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
        return installedTables;
    }

    private static Set<String> findSqliteInstallTables(DataSourceWrapper dataSource) throws SQLException {
        Set<String> installedTables = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet tables = statement.executeQuery(SQLITE_TABLE_QUERY)) {
            while (tables.next()) {
                String tableName = tables.getString(1);
                if (tableName != null) {
                    installedTables.add(tableName.toLowerCase(Locale.ROOT));
                }
            }
        }
        return installedTables;
    }

    static boolean isDropStatement(String sql) {
        return sql != null && DROP_STATEMENT.matcher(sql).find();
    }
}
