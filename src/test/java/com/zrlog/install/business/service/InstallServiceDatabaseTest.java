package com.zrlog.install.business.service;

import com.hibegin.common.dao.DAO;
import com.hibegin.common.dao.ResultValueConvertUtils;
import com.zrlog.install.business.response.InstallProgressEvent;
import com.zrlog.install.business.type.TestConnectDbResult;
import com.zrlog.install.business.vo.DefaultWebsiteSettings;
import com.zrlog.install.business.vo.InstallConfigVO;
import com.zrlog.install.exception.InstallException;
import com.zrlog.install.support.TestDatabase;
import com.zrlog.install.util.LogCaptureSupport;
import com.zrlog.install.web.InstallConstants;
import com.zrlog.install.web.config.DefaultInstallConfig;
import org.junit.After;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
            assertEquals(DefaultWebsiteSettings.ADMIN_FIRST_USE_V4_PENDING, dao.queryFirstObj(
                    "select `value` from `website` where `name`='" +
                            DefaultWebsiteSettings.ADMIN_FIRST_USE_V4_KEY + "'"));
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

    @Test
    public void shouldProbeLocalSqliteWithoutClaimingTheTargetAndThenInstallSecurely() throws Exception {
        Assume.assumeTrue(database.isSqlite());
        File root = temporaryFolder.newFolder("zrlog-install-isolated-sqlite-probe");
        File dbFile = new File(root, "conf/db.properties");
        File lockFile = new File(root, "conf/install.lock");
        FakeInstallConfig config = new FakeInstallConfig(dbFile, lockFile);
        InstallService service = localSqliteService(root, config, "zrlog_isolated_probe");
        Path databaseFile = LocalSqliteSupport.getDatabaseFile(config).toPath();

        assertEquals(TestConnectDbResult.SUCCESS, service.testDbConn());

        assertFalse(Files.exists(databaseFile, LinkOption.NOFOLLOW_LINKS));
        try (var files = Files.list(databaseFile.getParent())) {
            assertEquals(0L, files.count());
        }

        assertTrue(service.install());
        assertTrue(Files.isRegularFile(databaseFile, LinkOption.NOFOLLOW_LINKS));
        if (Files.getFileStore(databaseFile).supportsFileAttributeView(PosixFileAttributeView.class)) {
            assertEquals(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(databaseFile, LinkOption.NOFOLLOW_LINKS));
        }
    }

    @Test
    public void shouldRejectExistingEmptyAndNonDatabaseSqliteFilesWithoutChangingThem() throws Exception {
        Assume.assumeTrue(database.isSqlite());
        List<byte[]> existingContents = List.of(
                new byte[0], "unrelated-file-must-not-change".getBytes(StandardCharsets.UTF_8));
        for (int i = 0; i < existingContents.size(); i++) {
            File root = temporaryFolder.newFolder("zrlog-install-existing-sqlite-file-" + i);
            FakeInstallConfig config = new FakeInstallConfig(
                    new File(root, "conf/db.properties"), new File(root, "conf/install.lock"));
            Path databaseFile = LocalSqliteSupport.getDatabaseFile(config).toPath();
            Files.createDirectories(databaseFile.getParent());
            Files.write(databaseFile, existingContents.get(i));
            byte[] original = Files.readAllBytes(databaseFile);
            InstallService service = localSqliteService(root, config, "zrlog_existing_file_" + i);

            assertLocalSqliteConflict(service);

            assertArrayEquals(original, Files.readAllBytes(databaseFile));
            assertFalse(config.getDbPropertiesFile().exists());
            assertFalse(config.getAction().getLockFile().exists());
        }
    }

    @Test
    public void shouldRejectSqliteDatabaseContainingOnlyUnrelatedTablesWithoutChangingIt() throws Exception {
        Assume.assumeTrue(database.isSqlite());
        File root = temporaryFolder.newFolder("zrlog-install-unrelated-sqlite-table");
        FakeInstallConfig config = new FakeInstallConfig(
                new File(root, "conf/db.properties"), new File(root, "conf/install.lock"));
        Properties properties = LocalSqliteSupport.createDatabaseConfig(config).toProperties();
        String marker = "unrelated-table-data-must-survive";
        LocalSqliteSupport.ensureDatabaseDirectory(config);
        try (var dataSource = InstallService.buildDataSource(properties, true)) {
            DAO dao = new DAO(dataSource);
            dao.execute("CREATE TABLE `unrelated` (`id` int PRIMARY KEY, `value` varchar(255))");
            assertTrue(dao.execute("INSERT INTO `unrelated` (`id`, `value`) VALUES (1, ?)", marker));
        }
        Path databaseFile = LocalSqliteSupport.getDatabaseFile(config).toPath();
        byte[] original = Files.readAllBytes(databaseFile);
        InstallService service = localSqliteService(root, config, "zrlog_unrelated_table");

        assertLocalSqliteConflict(service);

        assertArrayEquals(original, Files.readAllBytes(databaseFile));
        try (var dataSource = InstallService.buildDataSource(properties, true)) {
            assertEquals(marker, new DAO(dataSource)
                    .queryFirstObj("SELECT `value` FROM `unrelated` WHERE `id` = 1"));
        }
    }

    @Test
    public void shouldRejectRegularAndDanglingSqliteSymlinksWithoutFollowingThem() throws Exception {
        Assume.assumeTrue(database.isSqlite());
        File regularRoot = temporaryFolder.newFolder("zrlog-install-regular-sqlite-link");
        FakeInstallConfig regularConfig = new FakeInstallConfig(
                new File(regularRoot, "conf/db.properties"), new File(regularRoot, "conf/install.lock"));
        Path regularDatabaseFile = LocalSqliteSupport.getDatabaseFile(regularConfig).toPath();
        Path externalTarget = regularRoot.toPath().resolve("external-target.db");
        byte[] externalContents = "external-target-must-not-change".getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(regularDatabaseFile.getParent());
        Files.write(externalTarget, externalContents);
        createSymbolicLinkOrSkip(regularDatabaseFile, externalTarget);
        Path originalLinkTarget = Files.readSymbolicLink(regularDatabaseFile);

        assertLocalSqliteConflict(localSqliteService(regularRoot, regularConfig, "zrlog_regular_link"));

        assertTrue(Files.isSymbolicLink(regularDatabaseFile));
        assertEquals(originalLinkTarget, Files.readSymbolicLink(regularDatabaseFile));
        assertArrayEquals(externalContents, Files.readAllBytes(externalTarget));

        File danglingRoot = temporaryFolder.newFolder("zrlog-install-dangling-sqlite-link");
        FakeInstallConfig danglingConfig = new FakeInstallConfig(
                new File(danglingRoot, "conf/db.properties"), new File(danglingRoot, "conf/install.lock"));
        Path danglingDatabaseFile = LocalSqliteSupport.getDatabaseFile(danglingConfig).toPath();
        Path missingTarget = danglingRoot.toPath().resolve("missing-target.db");
        Files.createDirectories(danglingDatabaseFile.getParent());
        createSymbolicLinkOrSkip(danglingDatabaseFile, missingTarget);
        Path originalDanglingTarget = Files.readSymbolicLink(danglingDatabaseFile);

        assertLocalSqliteConflict(localSqliteService(danglingRoot, danglingConfig, "zrlog_dangling_link"));

        assertTrue(Files.isSymbolicLink(danglingDatabaseFile));
        assertEquals(originalDanglingTarget, Files.readSymbolicLink(danglingDatabaseFile));
        assertFalse(Files.exists(missingTarget, LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    public void shouldRejectExistingZrLogTableWithoutChangingItsData() throws Exception {
        File root = temporaryFolder.newFolder("zrlog-install-existing-table");
        File dbFile = new File(root, "conf/db.properties");
        File lockFile = new File(root, "conf/install.lock");
        FakeInstallConfig config = new FakeInstallConfig(dbFile, lockFile);
        Map<String, String> databaseValues;
        if (database.isSqlite()) {
            LocalSqliteSupport.ensureDatabaseDirectory(config);
            databaseValues = LocalSqliteSupport.createDatabaseConfig(config).toMap();
        } else {
            databaseValues = database.create(root, "zrlog_existing_table").asMap();
        }
        Properties properties = new Properties();
        properties.putAll(databaseValues);
        String marker = "existing-data-must-survive";
        try (var dataSource = InstallService.buildDataSource(properties, true)) {
            DAO dao = new DAO(dataSource);
            dao.execute("CREATE TABLE `log` (`logId` int PRIMARY KEY, `title` varchar(255))");
            assertTrue(dao.execute("INSERT INTO `log` (`logId`, `title`) VALUES (1, ?)", marker));
        }
        InstallConfigVO installConfigVO = installConfigVO(validSiteConfig(), null, null);
        installConfigVO.setDbConfig(databaseValues);
        InstallService service = new InstallService(config, installConfigVO);

        assertEquals(TestConnectDbResult.DATABASE_NOT_EMPTY, service.testDbConn());
        InstallException exception = assertThrows(InstallException.class, service::install);

        assertEquals(TestConnectDbResult.DATABASE_NOT_EMPTY.name(), exception.getCode());
        assertFalse(exception.getMessage().contains(marker));
        assertFalse(dbFile.exists());
        assertFalse(lockFile.exists());
        try (var dataSource = InstallService.buildDataSource(properties, true)) {
            DAO dao = new DAO(dataSource);
            assertEquals(marker, dao.queryFirstObj("SELECT `title` FROM `log` WHERE `logId`=1"));
            assertEquals(1L, ((Number) dao.queryFirstObj("SELECT count(1) FROM `log`")).longValue());
        }
    }

    @Test
    public void shouldRollBackCompletionStateAndAllowRetryWhenHostCallbackFails() throws Exception {
        File root = temporaryFolder.newFolder("zrlog-install-callback-retry");
        File dbFile = new File(root, "conf/db.properties");
        File lockFile = new File(root, "conf/install.lock");
        FakeInstallConfig config = new FakeInstallConfig(dbFile, lockFile);
        String installFailure = "SELECT password FROM user WHERE password='install-do-not-expose'";
        String recoveryFailure = "jdbc:mysql://recovery:3306/zrlog?password=resume-do-not-expose";
        config.failNextInstallSuccess(new IllegalStateException(installFailure));
        InstallConfigVO installConfigVO = installConfigVO(validSiteConfig(), null, null);
        installConfigVO.setDbConfig(database.create(root, "zrlog_callback_retry").asMap());
        List<InstallProgressEvent> firstAttemptEvents = new ArrayList<>();

        try (LogCaptureSupport logs = LogCaptureSupport.capture(InstallService.class)) {
            boolean firstAttempt = new InstallService(config, installConfigVO, firstAttemptEvents::add).install();

            assertFalse(firstAttempt);
            assertTrue(config.isInstallLockVisibleDuringCallback());
            assertFalse(lockFile.exists());
            assertFalse(dbFile.exists());
            InstallProgressEvent error = firstAttemptEvents.get(firstAttemptEvents.size() - 1);
            assertEquals("config", error.getCode());
            assertEquals("error", error.getStatus());
            assertFalse(error.getDetail().contains("do-not-expose"));
            assertTrue(new InstallRecoveryStore().isAvailable(lockFile));
            if (database.isSqlite()) {
                assertTrue(Files.isRegularFile(LocalSqliteSupport.getDatabaseFile(config).toPath(),
                        LinkOption.NOFOLLOW_LINKS));
            }
            assertTrue(logs.text().contains("phase=install"));
            assertFalse(logs.text().contains("install-do-not-expose"));
            assertFalse(logs.hasThrown());

            config.failNextInstallSuccess(new IllegalStateException(recoveryFailure));
            assertFalse(new InstallService(config, new InstallConfigVO()).resume());
            assertTrue(new InstallRecoveryStore().isAvailable(lockFile));
            assertTrue(logs.text().contains("phase=install-recovery"));
            assertFalse(logs.text().contains("resume-do-not-expose"));
            assertFalse(logs.text().contains("jdbc:mysql"));
            assertFalse(logs.hasThrown());

            assertTrue(new InstallService(config, new InstallConfigVO()).resume());
            assertEquals(3, config.getInstallSuccessCalls());
            assertTrue(dbFile.exists());
            assertTrue(lockFile.exists());
            assertFalse(new InstallRecoveryStore().isAvailable(lockFile));
        }
    }

    @Test
    public void shouldRefuseRecoveryWhenCoreSeedDataIsIncomplete() throws Exception {
        File root = temporaryFolder.newFolder("zrlog-install-incomplete-recovery");
        File dbFile = new File(root, "conf/db.properties");
        File lockFile = new File(root, "conf/install.lock");
        FakeInstallConfig config = new FakeInstallConfig(dbFile, lockFile);
        config.failNextInstallSuccess(new IllegalStateException("simulated callback failure"));
        InstallConfigVO installConfigVO = installConfigVO(validSiteConfig(), null, null);
        TestDatabase.Configuration databaseConfig = database.create(root, "zrlog_incomplete_recovery");
        installConfigVO.setDbConfig(databaseConfig.asMap());

        assertFalse(new InstallService(config, installConfigVO).install());
        assertTrue(new InstallRecoveryStore().isAvailable(lockFile));
        Properties recovered = new InstallRecoveryStore()
                .load(lockFile).orElseThrow().toProperties();
        try (var dataSource = InstallService.buildDataSource(recovered, true)) {
            assertTrue(new DAO(dataSource).execute("DELETE FROM `user` WHERE `userId` = 1"));
        }

        assertFalse(new InstallService(config, new InstallConfigVO()).resume());
        assertEquals(1, config.getInstallSuccessCalls());
        assertFalse(dbFile.exists());
        assertFalse(lockFile.exists());
        assertTrue(new InstallRecoveryStore().isAvailable(lockFile));
    }

    @Test
    public void shouldCompleteInstallWhenProgressListenerFailsAtEveryStage() throws Exception {
        File root = temporaryFolder.newFolder("zrlog-install-listener-failure");
        File dbFile = new File(root, "conf/db.properties");
        File lockFile = new File(root, "conf/install.lock");
        FakeInstallConfig config = new FakeInstallConfig(dbFile, lockFile);
        InstallConfigVO installConfigVO = installConfigVO(validSiteConfig(), null, null);
        installConfigVO.setDbConfig(database.create(root, "zrlog_listener_failure").asMap());
        AtomicInteger deliveryAttempts = new AtomicInteger();

        boolean installed = new InstallService(config, installConfigVO, event -> {
            deliveryAttempts.incrementAndGet();
            throw new java.io.IOException("client disconnected");
        }).install();

        assertTrue(installed);
        assertEquals(14, deliveryAttempts.get());
        assertTrue(dbFile.exists());
        assertTrue(lockFile.exists());
    }

    private static Map<String, String> validSiteConfig() {
        Map<String, String> configMsg = new LinkedHashMap<>();
        configMsg.put("title", "Database Blog");
        configMsg.put("second_title", "Fast install");
        configMsg.put("username", "admin");
        configMsg.put("password", "password");
        configMsg.put("email", "admin@example.com");
        configMsg.put("installDate", "2026-06-29 10:20:30 +0800");
        return configMsg;
    }

    private InstallService localSqliteService(File root, FakeInstallConfig config, String databaseName) {
        InstallConfigVO installConfigVO = installConfigVO(validSiteConfig(), null, null);
        installConfigVO.setDbConfig(database.create(root, databaseName).asMap());
        return new InstallService(config, installConfigVO);
    }

    private static void assertLocalSqliteConflict(InstallService service) {
        assertEquals(TestConnectDbResult.DATABASE_NOT_EMPTY, service.testDbConn());
        InstallException exception = assertThrows(InstallException.class, service::install);
        assertEquals(TestConnectDbResult.DATABASE_NOT_EMPTY.name(), exception.getCode());
    }

    private static void createSymbolicLinkOrSkip(Path link, Path target) throws Exception {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException e) {
            Assume.assumeNoException("Symbolic links are not available on this filesystem", e);
        }
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
