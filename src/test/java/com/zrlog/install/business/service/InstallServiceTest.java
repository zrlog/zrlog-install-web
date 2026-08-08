package com.zrlog.install.business.service;

import com.hibegin.common.dao.DAO;
import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.dao.InMemoryDatabase;
import com.hibegin.common.dao.SqlConvertUtils;
import com.zrlog.install.business.type.TestConnectDbResult;
import com.zrlog.install.business.vo.DefaultWebsiteSettings;
import com.zrlog.install.business.vo.InstallConfigVO;
import com.zrlog.install.business.vo.InstallSiteConfig;
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
import java.util.Properties;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class InstallServiceTest {

    @After
    public void tearDown() {
        InstallConstants.installConfig = new DefaultInstallConfig();
    }

    @Test
    public void shouldInstallSchemaAndSeedDataUsingInMemoryDatabase() throws Exception {
        File root = Files.createTempDirectory("zrlog-install-service").toFile();
        File dbFile = new File(root, "conf/db.properties");
        File lockFile = new File(root, "conf/install.lock");
        FakeInstallConfig config = new FakeInstallConfig(dbFile, lockFile);
        InstallConstants.installConfig = config;
        List<com.zrlog.install.business.response.InstallProgressEvent> events = new ArrayList<>();
        Map<String, String> configMsg = new LinkedHashMap<>();
        configMsg.put("title", "H2 Blog");
        configMsg.put("second_title", "Fast install");
        configMsg.put("username", "admin");
        configMsg.put("password", "password");
        configMsg.put("email", "admin@example.com");
        configMsg.put("installDate", "2026-06-29 10:20:30 +0800");
        Map<String, String> appendWebsite = new LinkedHashMap<>();
        appendWebsite.put("host", "https://example.com");
        InstallConfigVO installConfigVO = installConfigVO(configMsg, appendWebsite, "/blog");
        installConfigVO.setDbConfig(h2DbConfig());

        boolean installed = new InstallService(config, installConfigVO, events::add).install();

        assertTrue(installed);
        assertTrue(dbFile.exists());
        assertTrue(lockFile.exists());
        Properties stored = new Properties();
        try (var input = Files.newInputStream(dbFile.toPath())) {
            stored.load(input);
        }
        assertEquals(InMemoryDatabase.H2_DRIVER_CLASS, stored.getProperty("driverClass"));
        assertEquals("sa", stored.getProperty("user"));
        try (var ds = InstallService.buildDataSource(stored, true)) {
            DAO dao = new DAO(ds);
            assertEquals(1L, ((Number) dao.queryFirstObj("select count(1) from `user`")).longValue());
            assertEquals(1L, ((Number) dao.queryFirstObj("select count(1) from `log`")).longValue());
            assertEquals(2L, ((Number) dao.queryFirstObj("select count(1) from `lognav`")).longValue());
            assertEquals(4L, ((Number) dao.queryFirstObj("select count(1) from `plugin`")).longValue());
            assertEquals(0L, ((Number) dao.queryFirstObj(
                    "select `sticky` from `log` where `logId`=1")).longValue());
            assertEquals(0L, ((Number) dao.queryFirstObj("select count(`extensions`) from `log`")).longValue());
            assertEquals(0L, ((Number) dao.queryFirstObj(
                    "select count(1) from `log_extension_index`")).longValue());
            assertNull(dao.queryFirstObj("select `passkeyUserHandle` from `user` where `userId`=1"));
            assertEquals(0L, ((Number) dao.queryFirstObj("select count(1) from `user_passkey`")).longValue());
            assertEquals(0L, ((Number) dao.queryFirstObj(
                    "select count(1) from `user_passkey_challenge`")).longValue());
            assertEquals("26", dao.queryFirstObj(
                    "select `value` from `website` where `name`='zrlogSqlVersion'"));
            assertEquals("H2 Blog", dao.queryFirstObj("select `value` from `website` where `name`='title'"));
            assertEquals("https://example.com", dao.queryFirstObj("select `value` from `website` where `name`='host'"));
            assertEquals("admin", dao.queryFirstObj("select `userName` from `user` where `userId`=1"));
        }
        assertEquals(List.of("preflight:running", "preflight:complete", "database:running", "database:complete",
                "schema:running", "schema:complete", "seed-website:running", "seed-website:complete",
                "seed-admin:running", "seed-admin:complete", "seed-defaults:running", "seed-defaults:complete",
                "config:running", "config:complete"), eventTrace(events));
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
    public void shouldBuildDefaultWebsiteSettingsWithAppendValues() throws Exception {
        File root = Files.createTempDirectory("zrlog-install-service").toFile();
        FakeInstallConfig config = new FakeInstallConfig(
                new File(root, "db.properties"),
                new File(root, "install.lock"));
        Map<String, String> configMsg = new HashMap<>();
        configMsg.put("title", "My Blog");
        Map<String, String> appendWebsite = new HashMap<>();
        appendWebsite.put("host", "example.com");
        InstallService service = new InstallService(config, installConfigVO(configMsg, appendWebsite, "/blog"));

        DefaultWebsiteSettings settings = service.getDefaultWebSiteSettings(InstallSiteConfig.from(configMsg));
        Map<?, ?> settingsMap = settings.toMap();

        assertEquals("My Blog", settings.getTitle());
        assertEquals("", settings.getSecondTitle());
        assertEquals("zh_CN", settings.getLanguage());
        assertEquals("/include/templates/default", settings.getTemplate());
        assertEquals("26", settings.getZrlogSqlVersion());
        assertEquals("example.com", settingsMap.get("host"));
        assertTrue(settingsMap.containsKey("appId"));
    }

    @Test
    public void shouldReturnFalseWhenAlreadyInstalled() throws Exception {
        File root = Files.createTempDirectory("zrlog-install-service").toFile();
        FakeInstallConfig config = new FakeInstallConfig(
                new File(root, "db.properties"),
                new File(root, "install.lock"));
        config.setInstalled(true);

        boolean installed = new InstallService(config,
                installConfigVO(Collections.emptyMap(), null, null)).install();

        assertFalse(installed);
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
        assertEquals("Missing parent directory for db.properties", events.get(1).getDetail());
    }

    @Test
    public void shouldReportMissingJdbcDriverWithoutOpeningConnection() throws Exception {
        File root = Files.createTempDirectory("zrlog-install-service").toFile();
        FakeInstallConfig config = new FakeInstallConfig(
                new File(root, "db.properties"),
                new File(root, "install.lock"));
        Map<String, String> dbConn = new HashMap<>();
        dbConn.put("driverClass", "missing.Driver");
        dbConn.put("jdbcUrl", "jdbc:missing:test");
        InstallConfigVO installConfigVO = installConfigVO(Collections.emptyMap(), null, null);
        installConfigVO.setDbConfig(dbConn);

        TestConnectDbResult result = new InstallService(config, installConfigVO).testDbConn();

        assertEquals(TestConnectDbResult.MISSING_JDBC_DRIVER, result);
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
    public void shouldReportSuccessfulConnectionForInMemoryDatabase() throws Exception {
        File root = Files.createTempDirectory("zrlog-install-service").toFile();
        FakeInstallConfig config = new FakeInstallConfig(
                new File(root, "db.properties"),
                new File(root, "install.lock"));
        InstallConfigVO installConfigVO = installConfigVO(Collections.emptyMap(), null, null);
        installConfigVO.setDbConfig(h2DbConfig());

        TestConnectDbResult result = new InstallService(config, installConfigVO).testDbConn();

        assertEquals(TestConnectDbResult.SUCCESS, result);
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
        assertEquals(18, call.args.length);
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

    @Test
    public void shouldSanitizeBlankAndLongProgressErrors() throws Exception {
        File root = Files.createTempDirectory("zrlog-install-service").toFile();
        InstallService service = new InstallService(new FakeInstallConfig(
                new File(root, "db.properties"),
                new File(root, "install.lock")), installConfigVO(Collections.emptyMap(), null, null));
        String longMessage = repeat("a", 220) + "\nsecond line";

        assertEquals("IllegalStateException", service.sanitizeError(new IllegalStateException(" ")));
        assertEquals(180, service.sanitizeError(new IllegalStateException(longMessage)).length());
        assertEquals("first line", service.sanitizeError(new IllegalStateException("first line\nsecond line")));
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

    private static Map<String, String> h2DbConfig() {
        Map<String, String> dbConfig = new LinkedHashMap<>();
        dbConfig.put("driverClass", InMemoryDatabase.H2_DRIVER_CLASS);
        dbConfig.put("jdbcUrl", InMemoryDatabase.h2JdbcUrl("zrlog_install_" + UUID.randomUUID()));
        dbConfig.put("user", "sa");
        dbConfig.put("password", "");
        dbConfig.put("dbType", "h2");
        dbConfig.put("dbName", "zrlog");
        dbConfig.put("dbHost", "localhost");
        dbConfig.put("dbPort", "0");
        return dbConfig;
    }

    private static List<String> eventTrace(List<com.zrlog.install.business.response.InstallProgressEvent> events) {
        List<String> trace = new ArrayList<>();
        for (com.zrlog.install.business.response.InstallProgressEvent event : events) {
            trace.add(event.getCode() + ":" + event.getStatus());
        }
        return trace;
    }

    private static String repeat(String value, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(value);
        }
        return sb.toString();
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
