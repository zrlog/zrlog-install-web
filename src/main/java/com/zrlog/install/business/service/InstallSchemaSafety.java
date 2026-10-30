package com.zrlog.install.business.service;

import com.hibegin.common.dao.DAO;
import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.install.business.vo.InstallDatabaseConfig;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;

final class InstallSchemaSafety {

    private static final Set<String> INSTALL_TABLES = Set.of(
            "link", "lognav", "plugin", "tag", "type", "user", "user_passkey",
            "user_passkey_challenge", "log", "log_extension_index", "comment", "log_version", "website");
    private static final String SQLITE_TABLE_QUERY = "SELECT name FROM sqlite_master "
            + "WHERE type IN ('table','view') AND lower(name) IN ("
            + "'link','lognav','plugin','tag','type','user','user_passkey','user_passkey_challenge',"
            + "'log','log_extension_index','comment','log_version','website')";

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
        DAO dao = new DAO(dataSource);
        return hasRows(dao, "SELECT COUNT(1) FROM `user` WHERE `userId` = 1", 1)
                && hasRows(dao, "SELECT COUNT(1) FROM `website` WHERE `name` IN "
                + "('zrlogSqlVersion','title','template','language')", 4)
                && hasRows(dao, "SELECT COUNT(1) FROM `lognav`", 2)
                && hasRows(dao, "SELECT COUNT(1) FROM `plugin`", 4)
                && hasRows(dao, "SELECT COUNT(1) FROM `type` WHERE `typeId` = 1", 1)
                && hasRows(dao, "SELECT COUNT(1) FROM `tag` WHERE `tagId` = 1", 1)
                && hasRows(dao, "SELECT COUNT(1) FROM `log` WHERE `logId` = 1", 1);
    }

    private static boolean hasRows(DAO dao, String sql, long minimum) throws SQLException {
        Object count = dao.queryFirstObj(sql);
        return count instanceof Number && ((Number) count).longValue() >= minimum;
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
        // WebAPI has a QueryRunner, but cannot expose a JDBC connection.
        for (var table : new DAO(dataSource).queryListWithParams(SQLITE_TABLE_QUERY)) {
            Object tableName = table.get("name");
            if (tableName != null) {
                installedTables.add(tableName.toString().toLowerCase(Locale.ROOT));
            }
        }
        return installedTables;
    }

    static boolean isDropStatement(String sql) {
        if (sql == null) {
            return false;
        }
        // Scan each leading comment once; a repeated regex alternative can backtrack
        // exponentially over adjacent block comments in an imported SQL file.
        int offset = 0;
        while (offset < sql.length()) {
            if (Character.isWhitespace(sql.charAt(offset)) || sql.charAt(offset) == '\uFEFF') {
                offset++;
            } else if (sql.startsWith("--", offset) || sql.charAt(offset) == '#') {
                while (offset < sql.length() && sql.charAt(offset) != '\r' && sql.charAt(offset) != '\n') {
                    offset++;
                }
            } else if (sql.startsWith("/*", offset)) {
                int end = sql.indexOf("*/", offset + 2);
                if (end < 0) {
                    return false;
                }
                offset = end + 2;
            } else {
                break;
            }
        }
        int afterKeyword = offset + 4;
        return sql.regionMatches(true, offset, "DROP", 0, 4)
                && (afterKeyword == sql.length()
                || Character.isWhitespace(sql.charAt(afterKeyword))
                || sql.startsWith("/*", afterKeyword));
    }
}
