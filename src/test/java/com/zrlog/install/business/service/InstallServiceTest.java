package com.zrlog.install.business.service;

import com.hibegin.common.dao.DAO;
import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.dao.InMemoryDatabase;
import com.hibegin.common.dao.SqlConvertUtils;
import com.hibegin.common.util.IOUtil;
import com.zrlog.install.business.type.TestConnectDbResult;
import com.zrlog.install.business.vo.DefaultWebsiteSettings;
import com.zrlog.install.business.vo.InstallConfigVO;
import com.zrlog.install.business.vo.InstallDatabaseConfig;
import com.zrlog.install.business.vo.InstallSiteConfig;
import com.zrlog.install.exception.InstallOperationInProgressException;
import com.zrlog.install.exception.InstalledException;
import com.zrlog.install.util.InstallI18nUtil;
import com.zrlog.install.util.LogCaptureSupport;
import com.zrlog.install.web.InstallAction;
import com.zrlog.install.web.InstallConstants;
import com.zrlog.install.web.config.DefaultInstallConfig;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class InstallServiceTest {

    @After
    public void tearDown() {
        InstallConstants.installConfig = new DefaultInstallConfig();
    }

    @Test
    public void shouldStripHtmlWhenBuildingPlainSearchText() throws Exception {
        assertEquals("", InstallService.getPlainSearchText(null));
        assertEquals("", InstallService.getPlainSearchText(""));
        assertEquals("hello world", InstallService.getPlainSearchText("<p>hello <strong>world</strong></p>"));
    }

    @Test
    public void shouldOnlyTreatCommaSeparatedDropTableAsBatchDropSql() {
        assertTrue(SqlConvertUtils.isBatchDropTableSql(" DROP TABLE IF EXISTS `log`, `comment` "));
        assertFalse(SqlConvertUtils.isBatchDropTableSql("DROP TABLE IF EXISTS `log`"));
        assertFalse(SqlConvertUtils.isBatchDropTableSql("DELETE FROM `log`, `comment`"));
    }

    @Test
    public void shouldClassifySqlExceptionMessagesWithoutAssumingAMessageExists() {
        assertFalse(InstallService.messageContainsAll(new SQLException((String) null), "Access denied"));
        assertFalse(InstallService.messageContainsAll(
                new SQLException("Access denied for user"), "Access denied for user", "using password"));
        assertTrue(InstallService.messageContainsAll(
                new SQLException("Access denied for user 'root' using password: YES"),
                "Access denied for user", "using password"));
        assertTrue(InstallService.messageContainsAll(
                new SQLException("Unknown database 'zrlog'"), "Unknown database"));
    }

    @Test
    public void shouldUseTheSameSqliteConverterForD1AndLocalSqlite() {
        String installSql = IOUtil.getStringInputStream(
                InstallService.class.getResourceAsStream("/init-table-structure.sql"));
        InstallDatabaseConfig d1 = new InstallDatabaseConfig();
        d1.setDbType("webapi");
        d1.setJdbcUrl("jdbc:webapi://example.com:443/zrlog");
        InstallDatabaseConfig localSqlite = new InstallDatabaseConfig();
        localSqlite.setDbType("sqlite");
        localSqlite.setDriverClass("org.sqlite.JDBC");
        localSqlite.setJdbcUrl("jdbc:sqlite:/tmp/zrlog.db");
        List<String> expected = SqlConvertUtils.doMySQLToSqliteBySqlText(installSql);

        assertEquals(expected, InstallService.prepareInstallSql(installSql, true, d1));
        assertEquals(expected, InstallService.prepareInstallSql(installSql, false, localSqlite));
        assertTrue(expected.stream().noneMatch(InstallSchemaSafety::isDropStatement));
    }

    @Test
    public void shouldBuildDefaultWebsiteSettingsWithAppendValues() throws Exception {
        File root = Files.createTempDirectory("zrlog-install-service").toFile();
        FakeInstallConfig config = new FakeInstallConfig(
                new File(root, "db.properties"),
                new File(root, "install.lock"));
        Map<String, String> configMsg = new HashMap<>();
        configMsg.put("title", "My Blog");
        Map<String, String> appendWebsite = new HashMap<>();
        appendWebsite.put("host", "example.com");
        appendWebsite.put(DefaultWebsiteSettings.ADMIN_FIRST_USE_V4_KEY, "dismissed");
        InstallService service = new InstallService(config, installConfigVO(configMsg, appendWebsite, "/blog"));

        DefaultWebsiteSettings settings = service.getDefaultWebSiteSettings(InstallSiteConfig.from(configMsg));
        Map<?, ?> settingsMap = settings.toMap();

        assertEquals("My Blog", settings.getTitle());
        assertEquals("", settings.getSecondTitle());
        assertEquals("zh_CN", settings.getLanguage());
        assertEquals("/include/templates/default", settings.getTemplate());
        assertEquals("26", settings.getZrlogSqlVersion());
        assertEquals("example.com", settingsMap.get("host"));
        assertEquals(DefaultWebsiteSettings.ADMIN_FIRST_USE_V4_PENDING,
                settingsMap.get(DefaultWebsiteSettings.ADMIN_FIRST_USE_V4_KEY));
        assertTrue(settingsMap.containsKey("appId"));
    }

    @Test
    public void shouldRejectWhenAlreadyInstalled() throws Exception {
        File root = Files.createTempDirectory("zrlog-install-service").toFile();
        FakeInstallConfig config = new FakeInstallConfig(
                new File(root, "db.properties"),
                new File(root, "install.lock"));
        config.setInstalled(true);

        InstalledException exception = assertThrows(InstalledException.class, () ->
                new InstallService(config,
                        installConfigVO(Collections.emptyMap(), null, null)).install());

        assertEquals("INSTALL_ALREADY_COMPLETED", exception.getCode());
    }

    @Test
    public void shouldSurfaceCompletionDetectedInsideTheCrossProcessLock() throws Exception {
        File root = Files.createTempDirectory("zrlog-install-completed-race").toFile();
        AtomicInteger installChecks = new AtomicInteger();
        FakeInstallConfig installConfig = installCompletesOnSecondCheck(root, installChecks);

        InstalledException installException = assertThrows(InstalledException.class, () ->
                new InstallService(installConfig,
                        installConfigVO(Collections.emptyMap(), null, null)).install());

        assertEquals("INSTALL_ALREADY_COMPLETED", installException.getCode());
        assertEquals(2, installChecks.get());

        AtomicInteger recoveryChecks = new AtomicInteger();
        FakeInstallConfig recoveryConfig = installCompletesOnSecondCheck(root, recoveryChecks);

        InstalledException recoveryException = assertThrows(InstalledException.class, () ->
                new InstallService(recoveryConfig, new InstallConfigVO()).resume());

        assertEquals("INSTALL_ALREADY_COMPLETED", recoveryException.getCode());
        assertEquals(2, recoveryChecks.get());
    }

    @Test
    public void shouldSurfaceCrossProcessInstallLockConflicts() throws Exception {
        File root = Files.createTempDirectory("zrlog-install-service-lock-conflict").toFile();
        FakeInstallConfig config = new FakeInstallConfig(
                new File(root, "db.properties"), new File(root, "install.lock"));
        InstallConfigVO request = installConfigVO(Collections.emptyMap(), null, null);

        try (InstallOperationLock ignored = InstallOperationLock.tryAcquire(
                config.getAction().getLockFile())) {
            assertNotNull(ignored);
            InstallOperationInProgressException installConflict = assertThrows(
                    InstallOperationInProgressException.class,
                    () -> new InstallService(config, request).install());
            InstallOperationInProgressException recoveryConflict = assertThrows(
                    InstallOperationInProgressException.class,
                    () -> new InstallService(config, request).resume());

            assertEquals("INSTALL_IN_PROGRESS", installConflict.getCode());
            assertEquals("INSTALL_IN_PROGRESS", recoveryConflict.getCode());
        }
    }

    @Test
    public void shouldEmitPreflightErrorBeforeOpeningDatabase() {
        FakeInstallConfig config = new FakeInstallConfig(new File("db.properties"), new File("install.lock"));
        List<com.zrlog.install.business.response.InstallProgressEvent> events = new ArrayList<>();

        boolean installed = new InstallService(config,
                installConfigVO(Collections.emptyMap(), null, null), events::add).install();

        assertFalse(installed);
        assertEquals(2, events.size());
        assertEquals("preflight", events.get(0).getCode());
        assertEquals("running", events.get(0).getStatus());
        assertEquals("preflight", events.get(1).getCode());
        assertEquals("error", events.get(1).getStatus());
        assertEquals(InstallI18nUtil.getInstallStringFromRes("streamFailed"), events.get(1).getDetail());
    }

    @Test
    public void shouldReportMissingJdbcDriverWithoutOpeningConnection() throws Exception {
        File root = Files.createTempDirectory("zrlog-install-service").toFile();
        FakeInstallConfig config = new FakeInstallConfig(
                new File(root, "db.properties"),
                new File(root, "install.lock"));
        Map<String, String> dbConn = new HashMap<>();
        dbConn.put("driverClass", "missing.do-not-expose.Driver");
        dbConn.put("jdbcUrl", "jdbc:missing:do-not-expose");
        dbConn.put("password", "do-not-expose");
        InstallConfigVO installConfigVO = installConfigVO(Collections.emptyMap(), null, null);
        installConfigVO.setDbConfig(dbConn);

        try (LogCaptureSupport logs = LogCaptureSupport.capture(InstallService.class)) {
            TestConnectDbResult result = new InstallService(config, installConfigVO).testDbConn();

            assertEquals(TestConnectDbResult.MISSING_JDBC_DRIVER, result);
            assertTrue(logs.text().contains("phase=database-connection-test"));
            assertTrue(logs.text().contains("exception=java.lang.ClassNotFoundException"));
            assertFalse(logs.text().contains("do-not-expose"));
            assertFalse(logs.hasThrown());
        }
    }

    @Test
    public void shouldReportMissingJdbcDriverWhenDriverClassIsAbsent() throws Exception {
        File root = Files.createTempDirectory("zrlog-install-service").toFile();
        FakeInstallConfig config = new FakeInstallConfig(
                new File(root, "db.properties"),
                new File(root, "install.lock"));
        Map<String, String> dbConn = new HashMap<>();
        dbConn.put("jdbcUrl", InMemoryDatabase.h2JdbcUrl("missing_driver"));
        InstallConfigVO installConfigVO = installConfigVO(Collections.emptyMap(), null, null);
        installConfigVO.setDbConfig(dbConn);

        TestConnectDbResult result = new InstallService(config, installConfigVO).testDbConn();

        assertEquals(TestConnectDbResult.MISSING_JDBC_DRIVER, result);
    }

    @Test
    public void shouldRejectLocalSqliteWhenPackageDoesNotSupportIt() throws Exception {
        File root = Files.createTempDirectory("zrlog-install-service-war-sqlite").toFile();
        FakeInstallConfig config = new FakeInstallConfig(
                new File(root, "db.properties"), new File(root, "install.lock"));
        config.setWarMode(true);
        InstallConfigVO installConfigVO = installConfigVO(Collections.emptyMap(), null, null);
        Map<String, String> dbConfig = new LinkedHashMap<>();
        dbConfig.put("dbType", "sqlite");
        dbConfig.put("driverClass", "org.sqlite.JDBC");
        dbConfig.put("jdbcUrl", "jdbc:sqlite:" + new File(root, "zrlog.db").getAbsolutePath());
        dbConfig.put("user", "");
        dbConfig.put("password", "");
        installConfigVO.setDbConfig(dbConfig);

        InstallService service = new InstallService(config, installConfigVO);

        assertEquals(TestConnectDbResult.UNSUPPORTED_DATABASE, service.testDbConn());
        assertFalse(service.install());
        assertFalse(config.getAction().getLockFile().exists());
    }

    @Test
    public void shouldBuildWebsiteSeedSqlAndParams() throws Exception {
        File root = Files.createTempDirectory("zrlog-install-service").toFile();
        FakeInstallConfig config = new FakeInstallConfig(
                new File(root, "db.properties"),
                new File(root, "install.lock"));
        Map<String, String> configMsg = new LinkedHashMap<>();
        configMsg.put("title", "My Blog");
        configMsg.put("second_title", "Notes");
        Map<String, String> appendWebsite = new LinkedHashMap<>();
        appendWebsite.put("host", "example.com");
        InstallService service = new InstallService(config, installConfigVO(configMsg, appendWebsite, "/blog"));
        FakeDao dao = new FakeDao(true);

        assertTrue(service.initWebSite(dao));

        assertEquals(1, dao.calls.size());
        FakeDao.Call call = dao.calls.get(0);
        assertTrue(call.sql.startsWith("INSERT INTO `website` (`name`, `value`) VALUES"));
        assertEquals(20, call.args.length);
        assertEquals("appId", call.args[0]);
        assertNotNull(call.args[1]);
        assertEquals("title", call.args[2]);
        assertEquals("My Blog", call.args[3]);
        assertEquals("second_title", call.args[4]);
        assertEquals("Notes", call.args[5]);
        assertEquals("language", call.args[6]);
        assertEquals("zh_CN", call.args[7]);
        assertEquals("host", call.args[16]);
        assertEquals("example.com", call.args[17]);
        assertEquals(DefaultWebsiteSettings.ADMIN_FIRST_USE_V4_KEY, call.args[18]);
        assertEquals(DefaultWebsiteSettings.ADMIN_FIRST_USE_V4_PENDING, call.args[19]);
    }

    @Test
    public void shouldBuildUserSeedSqlWithProvidedSecret() throws Exception {
        File root = Files.createTempDirectory("zrlog-install-service").toFile();
        FakeInstallConfig config = new FakeInstallConfig(
                new File(root, "db.properties"),
                new File(root, "install.lock"));
        Map<String, String> configMsg = new HashMap<>();
        configMsg.put("username", "admin");
        configMsg.put("password", "password");
        configMsg.put("email", "admin@example.com");
        configMsg.put("secretKey", "secret");
        InstallService service = new InstallService(config, installConfigVO(configMsg, null, null));
        FakeDao dao = new FakeDao(true);

        assertTrue(service.initUser(InstallSiteConfig.from(configMsg), dao));

        assertEquals(1, dao.calls.size());
        FakeDao.Call call = dao.calls.get(0);
        assertTrue(call.sql.startsWith("INSERT INTO `user`"));
        assertEquals("admin", call.args[0]);
        assertEquals("password", call.args[1]);
        assertEquals("admin@example.com", call.args[2]);
        assertEquals("secret", call.args[3]);
    }

    @Test
    public void shouldGenerateUserSecretWhenMissing() throws Exception {
        File root = Files.createTempDirectory("zrlog-install-service").toFile();
        FakeInstallConfig config = new FakeInstallConfig(
                new File(root, "db.properties"),
                new File(root, "install.lock"));
        Map<String, String> configMsg = new HashMap<>();
        configMsg.put("username", "admin");
        configMsg.put("password", "password");
        configMsg.put("email", "admin@example.com");
        InstallService service = new InstallService(config, installConfigVO(configMsg, null, null));
        FakeDao dao = new FakeDao(true);

        assertTrue(service.initUser(InstallSiteConfig.from(configMsg), dao));

        assertEquals(36, dao.calls.get(0).args[3].toString().length());
    }

    @Test
    public void shouldBuildDefaultSeedSqlStatements() throws Exception {
        File root = Files.createTempDirectory("zrlog-install-service").toFile();
        FakeInstallConfig config = new FakeInstallConfig(
                new File(root, "db.properties"),
                new File(root, "install.lock"));
        InstallConstants.installConfig = config;
        InstallService service = new InstallService(config,
                installConfigVO(new HashMap<>(), null, null));
        FakeDao dao = new FakeDao(true);

        assertTrue(service.insertNav(dao));
        assertTrue(service.insertType(dao));
        assertTrue(service.insertTag(dao));
        assertTrue(service.initPlugin(dao));

        assertEquals(5, dao.calls.size());
        assertTrue(dao.calls.get(0).sql.startsWith("INSERT INTO `lognav`"));
        assertEquals("/", dao.calls.get(0).args[1]);
        assertEquals("/admin/login", dao.calls.get(1).args[1]);
        assertTrue(dao.calls.get(2).sql.startsWith("INSERT INTO `type`"));
        assertTrue(dao.calls.get(3).sql.startsWith("INSERT INTO `tag`"));
        assertTrue(dao.calls.get(4).sql.startsWith("INSERT INTO `plugin`"));
    }

    @Test
    public void shouldBuildFirstArticleSeedWithConfiguredInstallDate() throws Exception {
        File root = Files.createTempDirectory("zrlog-install-service").toFile();
        FakeInstallConfig config = new FakeInstallConfig(
                new File(root, "db.properties"),
                new File(root, "install.lock"));
        InstallConstants.installConfig = config;
        Map<String, String> configMsg = new HashMap<>();
        configMsg.put("installDate", "2026-06-29 10:20:30 +0800");
        InstallService service = new InstallService(config, installConfigVO(configMsg, null, "/blog"));
        FakeDao dao = new FakeDao(true);

        assertTrue(service.insertFirstArticle(dao));

        assertEquals(1, dao.calls.size());
        FakeDao.Call call = dao.calls.get(0);
        Date expected = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z").parse("2026-06-29 10:20:30 +0800");
        assertTrue(call.sql.startsWith("INSERT INTO `log`"));
        assertEquals(true, call.args[0]);
        assertEquals("hello-world", call.args[2]);
        assertTrue(call.args[4].toString().contains("<"));
        assertTrue(call.args[5].toString().length() > 0);
        assertTrue(call.args[6].toString().contains("/blog/admin/article-edit?id=1"));
        assertEquals(expected, call.args[8]);
        assertEquals(expected, call.args[9]);
        assertEquals(false, call.args[10]);
        assertEquals(false, call.args[11]);
    }

    @Test
    public void shouldBuildFirstArticleSeedWithCurrentDateWhenInstallDateIsMissing() throws Exception {
        File root = Files.createTempDirectory("zrlog-install-service").toFile();
        FakeInstallConfig config = new FakeInstallConfig(
                new File(root, "db.properties"),
                new File(root, "install.lock"));
        InstallConstants.installConfig = config;
        InstallService service = new InstallService(config, installConfigVO(new HashMap<>(), null, "/blog"));
        FakeDao dao = new FakeDao(true);

        assertTrue(service.insertFirstArticle(dao));

        FakeDao.Call call = dao.calls.get(0);
        assertTrue(call.args[8] instanceof Date);
        assertTrue(call.args[9] instanceof Date);
    }

    private static FakeInstallConfig installCompletesOnSecondCheck(File root, AtomicInteger checks) {
        File installLock = new File(root, "install.lock");
        InstallAction action = new InstallAction() {
            @Override
            public void installSuccess() {
            }

            @Override
            public File getLockFile() {
                return installLock;
            }

            @Override
            public boolean isInstalled() {
                return checks.incrementAndGet() >= 2;
            }
        };
        return new FakeInstallConfig(new File(root, "db.properties"), installLock) {
            @Override
            public InstallAction getAction() {
                return action;
            }
        };
    }

    private static InstallConfigVO installConfigVO(Map<String, String> configMsg,
                                                  Map<String, String> appendWebsite,
                                                  String contextPath) {
        InstallConfigVO installConfigVO = new InstallConfigVO();
        installConfigVO.setDbConfig(Collections.emptyMap());
        installConfigVO.setConfigMsg(configMsg);
        installConfigVO.setAppendWebsite(appendWebsite);
        installConfigVO.setContextPath(contextPath);
        return installConfigVO;
    }

    private static class FakeDao extends DAO {

        private final boolean result;
        private final List<Call> calls = new ArrayList<>();

        private FakeDao(boolean result) {
            super(dataSource());
            this.result = result;
        }

        @Override
        public boolean execute(String sql, Object... params) throws SQLException {
            calls.add(new Call(sql, params));
            return result;
        }

        private static class Call {
            private final String sql;
            private final Object[] args;

            private Call(String sql, Object[] args) {
                this.sql = sql;
                this.args = args;
            }
        }

        private static DataSourceWrapper dataSource() {
            return (DataSourceWrapper) java.lang.reflect.Proxy.newProxyInstance(
                    InstallServiceTest.class.getClassLoader(),
                    new Class[]{DataSourceWrapper.class},
                    (proxy, method, args) -> {
                        if ("isWebApi".equals(method.getName()) || "isDev".equals(method.getName())) {
                            return false;
                        }
                        if ("toString".equals(method.getName())) {
                            return "DataSourceWrapperProxy";
                        }
                        return null;
                    });
        }
    }
}
