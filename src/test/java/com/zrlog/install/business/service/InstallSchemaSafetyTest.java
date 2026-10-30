package com.zrlog.install.business.service;

import com.hibegin.common.dao.DAO;
import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.install.business.vo.InstallConfigVO;
import com.zrlog.install.business.vo.InstallDatabaseConfig;
import com.zrlog.install.support.InstallApplications;
import com.zrlog.install.support.TestDatabase;
import com.zrlog.install.web.InstallAction;
import com.zrlog.install.web.config.DefaultInstallAction;
import com.zrlog.install.web.config.DefaultInstallConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InstallSchemaSafetyTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void webApiProbeMustDetectExistingTablesWithoutRequestingJdbcConnection() throws Exception {
        var database = TestDatabase.SQLITE.create(temporary.newFolder(), "probe");
        try (var local = InstallService.buildDataSource(database.asProperties(), false)) {
            DataSourceWrapper webApi = withoutJdbc(local);
            var configuration = InstallDatabaseConfig.from(Map.of("dbType", "webapi"));
            assertFalse(InstallSchemaSafety.containsInstallTable(webApi, configuration));
            new DAO(local).execute("CREATE TABLE unrelated (id INTEGER)");
            assertFalse(InstallSchemaSafety.containsInstallTable(webApi, configuration));
            new DAO(local).execute("CREATE TABLE website (name TEXT, value TEXT)");
            assertTrue(InstallSchemaSafety.containsInstallTable(webApi, configuration));
            assertFalse(InstallSchemaSafety.containsCompleteInstallState(webApi, configuration));
        }
    }

    @Test
    public void webApiRecoveryMustVerifyRealInstalledStateWithoutRequestingJdbcConnection() throws Exception {
        File root = temporary.newFolder();
        File properties = new File(root, "conf/db.properties");
        File lock = new File(root, "conf/install.lock");
        var config = new DefaultInstallConfig() {
            @Override public File getDbPropertiesFile() { return properties; }
            @Override public InstallAction getAction() {
                return new DefaultInstallAction() {
                    @Override public File getLockFile() { return lock; }
                };
            }
        };
        InstallConfigVO request = new InstallConfigVO();
        request.setDbConfig(Map.of("dbType", "sqlite"));
        request.setConfigMsg(Map.of("title", "WebAPI recovery", "username", "admin", "password", "local-test-password"));
        assertTrue(new InstallApplications().service(config, request).install());
        try (var local = InstallService.buildDataSource(LocalSqliteSupport.createDatabaseConfig(config).toProperties(), false)) {
            DataSourceWrapper webApi = withoutJdbc(local);
            var configuration = InstallDatabaseConfig.from(Map.of("dbType", "webapi"));
            assertTrue(InstallSchemaSafety.containsCompleteInstallState(webApi, configuration));
            new DAO(local).execute("DELETE FROM website WHERE name='title'");
            assertTrue(InstallSchemaSafety.containsCompleteInstallSchema(webApi, configuration));
            assertFalse(InstallSchemaSafety.containsCompleteInstallState(webApi, configuration));
        }
    }

    private static DataSourceWrapper withoutJdbc(DataSourceWrapper delegate) {
        return (DataSourceWrapper) Proxy.newProxyInstance(InstallSchemaSafetyTest.class.getClassLoader(),
                new Class<?>[]{DataSourceWrapper.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isWebApi": return true;
                        case "isDev": return false;
                        case "getQueryRunner": return delegate.getQueryRunner();
                        default: throw new AssertionError("WebAPI must not use " + method.getName());
                    }
                });
    }

    @Test
    public void shouldRecognizeEveryDropTableStatementFormUsedByInstallSql() {
        assertTrue(InstallSchemaSafety.isDropStatement("DROP TABLE IF EXISTS `log`"));
        assertTrue(InstallSchemaSafety.isDropStatement("drop table if exists `log`, `comment`"));
        assertTrue(InstallSchemaSafety.isDropStatement("  DrOp TABLE `website`"));
        assertTrue(InstallSchemaSafety.isDropStatement("/* schema header */ DROP TABLE `user`"));
        assertTrue(InstallSchemaSafety.isDropStatement("-- schema header\nDROP TABLE `user`"));
        assertTrue(InstallSchemaSafety.isDropStatement("# schema header\nDROP/**/TABLE `user`"));
    }

    @Test
    public void shouldNotTreatNondestructiveStatementsOrStringValuesAsDropStatements() {
        assertFalse(InstallSchemaSafety.isDropStatement("CREATE TABLE `log` (`id` int)"));
        assertFalse(InstallSchemaSafety.isDropStatement("SELECT 'DROP TABLE user'"));
        assertFalse(InstallSchemaSafety.isDropStatement(null));
        assertFalse(InstallSchemaSafety.isDropStatement("DROPPER TABLE user"));
        assertFalse(InstallSchemaSafety.isDropStatement("DROP_foo TABLE user"));
        assertFalse(InstallSchemaSafety.isDropStatement("/* unterminated DROP TABLE user"));
        assertFalse(InstallSchemaSafety.isDropStatement("-- DROP TABLE user"));
        assertTrue(InstallSchemaSafety.isDropStatement("\uFEFF# comment\r-- next\n/**/DrOp/**/TABLE user"));
        assertTrue(InstallSchemaSafety.isDropStatement("DROP"));
    }

    @Test(timeout = 2000)
    public void shouldScanLongAdjacentCommentsWithoutBacktrackingOrStackOverflow() {
        String comments = "/**/".repeat(100000);
        assertFalse(InstallSchemaSafety.isDropStatement(comments + "SELECT 1"));
        assertTrue(InstallSchemaSafety.isDropStatement(comments + "DROP TABLE user"));
        assertFalse(InstallSchemaSafety.isDropStatement(comments + "/* unterminated"));
    }
}
