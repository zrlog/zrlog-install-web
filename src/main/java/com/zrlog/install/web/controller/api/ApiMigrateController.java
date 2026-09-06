package com.zrlog.install.web.controller.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.dao.DataSourceWrapperImpl;
import com.hibegin.common.dao.SqlConvertUtils;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.util.PathUtil;
import com.hibegin.http.server.web.Controller;
import com.zrlog.install.business.response.InstallApiResponses;
import com.zrlog.install.business.service.InstallOperationLock;
import com.zrlog.install.exception.InstallOperationInProgressException;
import com.zrlog.install.exception.InvalidInstallRequestException;
import com.zrlog.install.web.InstallConstants;
import com.zrlog.install.web.InstallRequestSecurity;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class ApiMigrateController extends Controller {

    private static final Logger LOGGER = LoggerUtil.getLogger(ApiMigrateController.class);
    private static final String MIGRATION_ID = "legacy-mysql-to-sqlite";
    private static final String MIGRATION_FIELD = "migration";
    private static final int MAX_REQUEST_BODY_BYTES = 1024;
    private static final Pattern SAFE_FILE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    public void convertToSqliteSqlFile() throws IOException {
        assertAuthorizedMigrationRequest();
        if (renderInstalled()) {
            return;
        }
        try (InstallOperationLock ignored = acquireOperationLock("conversion")) {
            if (renderInstalled()) {
                return;
            }
            try {
                convertMigrationFiles();
                response.renderJson(new InstallApiResponses.Empty());
            } catch (Exception e) {
                throw migrationFailure("conversion", e);
            }
        }
    }

    public void doImportSqlite() throws IOException {
        assertAuthorizedMigrationRequest();
        if (renderInstalled()) {
            return;
        }
        try (InstallOperationLock ignored = acquireOperationLock("import")) {
            if (renderInstalled()) {
                return;
            }
            try {
                convertMigrationFiles();
                importConvertedSql();
                response.renderJson(new InstallApiResponses.Empty());
            } catch (Exception e) {
                throw migrationFailure("import", e);
            }
        }
    }

    private static InstallOperationLock acquireOperationLock(String phase) throws IOException {
        InstallOperationLock operationLock;
        try {
            operationLock = InstallOperationLock.tryAcquire(
                    InstallConstants.installConfig.getAction().getLockFile());
        } catch (IOException e) {
            throw migrationFailure(phase, e);
        }
        if (operationLock == null) {
            throw new InstallOperationInProgressException("INSTALL");
        }
        return operationLock;
    }

    private void assertAuthorizedMigrationRequest() {
        HttpRequest request = getRequest();
        InstallRequestSecurity.assertMutationRequest(request);
        Map<String, String[]> parameters = request.getParamMap();
        String query = request.getQueryStr();
        if (!InstallRequestSecurity.isJsonContentType(request.getHeader("Content-Type"))
                || (parameters != null && !parameters.isEmpty())
                || (query != null && !query.trim().isEmpty())) {
            throw invalidRequest();
        }

        ByteBuffer requestBody = request.getRequestBodyByteBuffer();
        if (requestBody == null || !requestBody.hasRemaining()
                || requestBody.remaining() > MAX_REQUEST_BODY_BYTES) {
            throw invalidRequest();
        }
        ByteBuffer bodyCopy = requestBody.asReadOnlyBuffer();
        byte[] bodyBytes = new byte[bodyCopy.remaining()];
        bodyCopy.get(bodyBytes);
        String body = new String(bodyBytes, StandardCharsets.UTF_8).trim();
        if (body.isEmpty()) {
            throw invalidRequest();
        }
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonObject()) {
                throw invalidRequest();
            }
            JsonObject object = root.getAsJsonObject();
            JsonElement migration = object.get(MIGRATION_FIELD);
            if (object.size() != 1 || migration == null || !migration.isJsonPrimitive()
                    || !migration.getAsJsonPrimitive().isString()
                    || !MIGRATION_ID.equals(migration.getAsString())) {
                throw invalidRequest();
            }
        } catch (JsonParseException | IllegalStateException e) {
            throw invalidRequest();
        }
    }

    private boolean renderInstalled() {
        if (!InstallConstants.installConfig.getAction().isInstalled()) {
            return false;
        }
        response.renderCode(403);
        return true;
    }

    private void convertMigrationFiles() throws IOException {
        Path mysqlSource = resolveMigrationFile(MigrationFile.MYSQL_SOURCE, true);
        Path sqliteScript = resolveMigrationFile(MigrationFile.SQLITE_SCRIPT, false);
        List<String> converted = SqlConvertUtils.doMySQLToSqliteBySqlText(
                Files.readString(mysqlSource, StandardCharsets.UTF_8));
        StringJoiner script = new StringJoiner(";\n");
        converted.stream().filter(sql -> !isDropSql(sql)).forEach(script::add);
        writeAtomically(sqliteScript, script.toString());
    }

    private void importConvertedSql() throws Exception {
        Path sqliteScript = resolveMigrationFile(MigrationFile.SQLITE_SCRIPT, true);
        Path dbProperties = resolveMigrationFile(MigrationFile.SQLITE_DB_PROPERTIES, true);
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(dbProperties)) {
            properties.load(inputStream);
        }
        try (DataSourceWrapper dataSourceWrapper = new DataSourceWrapperImpl(properties, false)) {
            configureDataSource(dataSourceWrapper, properties);
            try (Connection connection = dataSourceWrapper.getConnection()) {
                boolean initialAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try (InputStream sqlInput = Files.newInputStream(sqliteScript)) {
                    for (String sql : SqlConvertUtils.extractExecutableSqlByInputStream(
                            sqlInput)) {
                        if (!isDropSql(sql)) {
                            executeStatement(connection, sql);
                        }
                    }
                    connection.commit();
                } catch (Exception e) {
                    try {
                        connection.rollback();
                    } catch (Exception ignored) {
                        // The public failure remains generic and contains no SQL or migration data.
                    }
                    throw e;
                } finally {
                    if (initialAutoCommit) {
                        connection.setAutoCommit(true);
                    }
                }
            }
        }
    }

    private static void configureDataSource(DataSourceWrapper dataSourceWrapper, Properties properties) {
        if (dataSourceWrapper instanceof DataSourceWrapperImpl && !dataSourceWrapper.isWebApi()) {
            DataSourceWrapperImpl dataSource = (DataSourceWrapperImpl) dataSourceWrapper;
            dataSource.setDriverClassName(properties.getProperty("driverClass"));
            dataSource.setJdbcUrl(properties.getProperty("jdbcUrl"));
            dataSource.setUsername(properties.getProperty("user"));
            dataSource.setPassword(properties.getProperty("password"));
        }
    }

    private static void executeStatement(Connection connection, String sql) throws Exception {
        if (sql.trim().toUpperCase(Locale.ROOT).startsWith("INSERT INTO")) {
            List<Object> values = SqlConvertUtils.extractValues(sql);
            String placeholders = values.stream().map(value -> "?")
                    .reduce((left, right) -> left + ", " + right).orElse("");
            int valuesIndex = sql.toUpperCase(Locale.ROOT).indexOf("VALUES");
            if (valuesIndex < 0) {
                throw new IllegalArgumentException("Invalid insert statement");
            }
            String preparedSql = sql.substring(0, valuesIndex + "VALUES".length())
                    + " (" + placeholders + ")";
            try (PreparedStatement statement = connection.prepareStatement(preparedSql)) {
                for (int i = 0; i < values.size(); i++) {
                    statement.setObject(i + 1, values.get(i));
                }
                statement.executeUpdate();
            }
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static Path resolveMigrationFile(MigrationFile migrationFile, boolean requireRegularFile)
            throws IOException {
        String fileName = migrationFile.fileName;
        if (!SAFE_FILE_NAME.matcher(fileName).matches() || fileName.contains("..")) {
            throw new IOException("Invalid server migration file mapping");
        }
        Path configDirectory = Path.of(PathUtil.getConfPath()).toAbsolutePath().normalize();
        Files.createDirectories(configDirectory);
        Path target = configDirectory.resolve(fileName).normalize();
        if (!configDirectory.equals(target.getParent()) || Files.isSymbolicLink(target)) {
            throw new IOException("Invalid server migration file mapping");
        }
        if (requireRegularFile && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Required server migration file is unavailable");
        }
        return target;
    }

    private static void writeAtomically(Path target, String content) throws IOException {
        Path temporaryFile = Files.createTempFile(target.getParent(), ".sqlite-migration-", ".tmp");
        boolean moved = false;
        try {
            Files.writeString(temporaryFile, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporaryFile, target,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                throw new IOException("Atomic migration file update is unavailable");
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporaryFile);
            }
        }
    }

    private static IOException migrationFailure(String phase, Exception failure) {
        LOGGER.warning("Legacy migration " + phase + " failed ["
                + failure.getClass().getSimpleName() + "]");
        return new IOException("Legacy migration " + phase + " failed");
    }

    private static InvalidInstallRequestException invalidRequest() {
        return new InvalidInstallRequestException("Invalid installation request");
    }

    protected static boolean isBatchDropTableSql(String sql) {
        String trimSql = sql.trim().toUpperCase(Locale.ROOT);
        return trimSql.startsWith("DROP TABLE IF EXISTS") && trimSql.contains(",");
    }

    protected static boolean isDropSql(String sql) {
        int offset = 0;
        while (offset < sql.length()) {
            char current = sql.charAt(offset);
            if (Character.isWhitespace(current) || current == '\uFEFF') {
                offset++;
                continue;
            }
            if (current == '#') {
                offset = skipLineComment(sql, offset + 1);
                continue;
            }
            if (current == '-' && offset + 1 < sql.length() && sql.charAt(offset + 1) == '-') {
                offset = skipLineComment(sql, offset + 2);
                continue;
            }
            if (current == '/' && offset + 1 < sql.length() && sql.charAt(offset + 1) == '*') {
                int commentEnd = sql.indexOf("*/", offset + 2);
                if (commentEnd < 0) {
                    return false;
                }
                if (offset + 2 < sql.length() && sql.charAt(offset + 2) == '!'
                        && isDropSql(sql.substring(offset + 3, commentEnd))) {
                    return true;
                }
                offset = commentEnd + 2;
                continue;
            }
            if (current == '\'' || current == '"' || current == '`') {
                offset = skipQuotedSql(sql, offset, current);
                continue;
            }
            if (current == '[') {
                offset = skipQuotedSql(sql, offset, ']');
                continue;
            }
            if (Character.isLetter(current)) {
                int keywordEnd = offset + 1;
                while (keywordEnd < sql.length() && isSqlIdentifierPart(sql.charAt(keywordEnd))) {
                    keywordEnd++;
                }
                if ("DROP".equalsIgnoreCase(sql.substring(offset, keywordEnd))) {
                    return true;
                }
                offset = keywordEnd;
                continue;
            }
            offset++;
        }
        return false;
    }

    private static int skipLineComment(String sql, int offset) {
        while (offset < sql.length() && sql.charAt(offset) != '\n' && sql.charAt(offset) != '\r') {
            offset++;
        }
        return offset;
    }

    private static int skipQuotedSql(String sql, int offset, char closingQuote) {
        offset++;
        while (offset < sql.length()) {
            char current = sql.charAt(offset);
            if (current == closingQuote) {
                if (offset + 1 < sql.length() && sql.charAt(offset + 1) == closingQuote) {
                    offset += 2;
                    continue;
                }
                return offset + 1;
            }
            offset++;
        }
        return offset;
    }

    private static boolean isSqlIdentifierPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '$';
    }

    private enum MigrationFile {
        MYSQL_SOURCE("mysql.sql"),
        SQLITE_SCRIPT("sqlite.sql"),
        SQLITE_DB_PROPERTIES("sqlite-db.properties");

        private final String fileName;

        MigrationFile(String fileName) {
            this.fileName = fileName;
        }
    }
}
