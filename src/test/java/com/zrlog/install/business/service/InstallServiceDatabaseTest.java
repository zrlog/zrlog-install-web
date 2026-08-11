package com.zrlog.install.business.service;

import com.hibegin.common.dao.DAO;
import com.hibegin.common.dao.ResultValueConvertUtils;
import com.zrlog.install.business.response.InstallProgressEvent;
import com.zrlog.install.business.type.TestConnectDbResult;
import com.zrlog.install.business.vo.InstallConfigVO;
import com.zrlog.install.support.TestDatabase;
import com.zrlog.install.web.InstallConstants;
import com.zrlog.install.web.config.DefaultInstallConfig;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class InstallServiceDatabaseTest {

    @Parameterized.Parameters(name = "{0}")
    public static TestDatabase[] databases() {
        return TestDatabase.values();
    }

    private final TestDatabase database;

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    public InstallServiceDatabaseTest(TestDatabase database) {
        this.database = database;
    }

    @After
    public void tearDown() {
        InstallConstants.installConfig = new DefaultInstallConfig();
    }

    @Test
    public void shouldInstallSchemaAndSeedDataUsingSupportedDatabase() throws Exception {
        File root = temporaryFolder.newFolder("zrlog-install-service");
        File dbFile = new File(root, "conf/db.properties");
        File lockFile = new File(root, "conf/install.lock");
        FakeInstallConfig config = new FakeInstallConfig(dbFile, lockFile);
        InstallConstants.installConfig = config;
        List<InstallProgressEvent> events = new ArrayList<>();
        Map<String, String> configMsg = new LinkedHashMap<>();
        configMsg.put("title", "Database Blog");
        configMsg.put("second_title", "Fast install");
        configMsg.put("username", "admin");
        configMsg.put("password", "password");
        configMsg.put("email", "admin@example.com");
        configMsg.put("installDate", "2026-06-29 10:20:30 +0800");
        Map<String, String> appendWebsite = new LinkedHashMap<>();
        appendWebsite.put("host", "https://example.com");
        InstallConfigVO installConfigVO = installConfigVO(configMsg, appendWebsite, "/blog");
        TestDatabase.Configuration databaseConfig = database.create(root, "zrlog_install");
        installConfigVO.setDbConfig(databaseConfig.asMap());

        boolean installed = new InstallService(config, installConfigVO, events::add).install();

        assertTrue(installed);
        assertTrue(dbFile.exists());
        assertTrue(lockFile.exists());
        Properties stored = new Properties();
        try (var input = Files.newInputStream(dbFile.toPath())) {
            stored.load(input);
        }
        assertEquals(database.driverClass(), stored.getProperty("driverClass"));
        assertEquals(databaseConfig.asMap().get("user"), stored.getProperty("user"));
        if (database.isSqlite()) {
            assertEquals(LocalSqliteSupport.createDatabaseConfig(config).getJdbcUrl(),
                    stored.getProperty("jdbcUrl"));
        }
        try (var ds = InstallService.buildDataSource(stored, true)) {
            DAO dao = new DAO(ds);
            for (String table : List.of("link", "lognav", "plugin", "tag", "type", "user", "user_passkey",
                    "user_passkey_challenge", "log", "log_extension_index", "comment", "log_version", "website")) {
                assertNotNull(table, dao.queryFirstObj("select count(1) from `" + table + "`"));
            }
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
            assertEquals("Database Blog", dao.queryFirstObj("select `value` from `website` where `name`='title'"));
            assertEquals("https://example.com", dao.queryFirstObj("select `value` from `website` where `name`='host'"));
            assertEquals("admin", dao.queryFirstObj("select `userName` from `user` where `userId`=1"));
            assertThrows(SQLException.class, () -> dao.execute(
                    "insert into `website` (`name`, `value`) values ('title', 'duplicate')"));
            if (database.isSqlite()) {
                assertEquals("wal", String.valueOf(dao.queryFirstObj("pragma journal_mode")).toLowerCase());
                assertEquals(1L, ((Number) dao.queryFirstObj("pragma foreign_keys")).longValue());
                assertEquals(10000L, ((Number) dao.queryFirstObj("pragma busy_timeout")).longValue());
                assertEquals("text", dao.queryFirstObj(
                        "select typeof(`releaseTime`) from `log` where `logId`=1"));
                assertEquals("text", dao.queryFirstObj(
                        "select typeof(`last_update_date`) from `log` where `logId`=1"));
                Object releaseTime = dao.queryFirstObj(
                        "select `releaseTime` from `log` where `logId`=1");
                assertEquals(19, String.valueOf(releaseTime).length());
                assertNotNull(ResultValueConvertUtils.parseDate(releaseTime));
            }
        }
        assertEquals(List.of("preflight:running", "preflight:complete", "database:running", "database:complete",
                "schema:running", "schema:complete", "seed-website:running", "seed-website:complete",
                "seed-admin:running", "seed-admin:complete", "seed-defaults:running", "seed-defaults:complete",
                "config:running", "config:complete"), eventTrace(events));
    }

    @Test
    public void shouldReportSuccessfulConnectionForSupportedDatabase() throws Exception {
        File root = temporaryFolder.newFolder("zrlog-install-service");
        FakeInstallConfig config = new FakeInstallConfig(
                new File(root, "db.properties"),
                new File(root, "install.lock"));
        InstallConfigVO installConfigVO = installConfigVO(Collections.emptyMap(), null, null);
        installConfigVO.setDbConfig(database.create(root, "zrlog_connection").asMap());

        TestConnectDbResult result = new InstallService(config, installConfigVO).testDbConn();

        assertEquals(TestConnectDbResult.SUCCESS, result);
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

    private static List<String> eventTrace(List<InstallProgressEvent> events) {
        List<String> trace = new ArrayList<>();
        for (InstallProgressEvent event : events) {
            trace.add(event.getCode() + ":" + event.getStatus());
        }
        return trace;
    }
}
