package com.zrlog.install;

import com.google.gson.Gson;
import com.zrlog.install.business.service.LocalSqliteSupport;
import com.zrlog.install.business.vo.InstallConfigVO;
import com.zrlog.install.support.TestDatabase;
import com.zrlog.install.web.InstallAction;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class ApplicationDatabaseTest {

    @Parameterized.Parameters(name = "{0}")
    public static TestDatabase[] databases() {
        return TestDatabase.values();
    }

    private final TestDatabase database;

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    public ApplicationDatabaseTest(TestDatabase database) {
        this.database = database;
    }

    @After
    public void tearDown() {
        InstallConstants.installConfig = new DefaultInstallConfig();
    }

    @Test
    public void shouldInstallFromConfigFileUsingSupportedDatabase() throws Exception {
        File root = temporaryFolder.newFolder("zrlog-application-install");
        File configFile = new File(root, "install.json");
        TestInstallConfig installConfig = new TestInstallConfig(
                new File(root, "conf/db.properties"),
                new File(root, "conf/install.lock"));
        InstallConstants.installConfig = installConfig;
        InstallConfigVO configVO = new InstallConfigVO();
        Map<String, String> configMsg = new LinkedHashMap<>();
        configMsg.put("title", "Application Install");
        configMsg.put("second_title", "Config file");
        configMsg.put("username", "admin");
        configMsg.put("password", "${github_pat}");
        configMsg.put("email", "admin@example.com");
        configMsg.put("installDate", "2026-06-29 10:20:30 +0800");
        configVO.setConfigMsg(configMsg);
        TestDatabase.Configuration databaseConfig = database.create(root, "zrlog_application_install");
        configVO.setDbConfig(databaseConfig.asMap());
        configVO.setContextPath("/blog");
        Files.writeString(configFile.toPath(), new Gson().toJson(configVO));

        Integer exitCode = Application.installFromConfigFile(new String[]{configFile.getAbsolutePath(), "password"});

        assertEquals(Integer.valueOf(0), exitCode);
        assertTrue(installConfig.dbPropertiesFile.exists());
        assertTrue(installConfig.lockFile.exists());
        assertTrue(installConfig.installSuccessCalled);
        if (database.isSqlite()) {
            Properties stored = new Properties();
            try (var input = Files.newInputStream(installConfig.dbPropertiesFile.toPath())) {
                stored.load(input);
            }
            assertEquals(LocalSqliteSupport.createDatabaseConfig(installConfig).getJdbcUrl(),
                    stored.getProperty("jdbcUrl"));
        }
    }

    private static class TestInstallConfig extends DefaultInstallConfig {

        private final File dbPropertiesFile;
        private final File lockFile;
        private boolean installSuccessCalled;

        TestInstallConfig(File dbPropertiesFile, File lockFile) {
            this.dbPropertiesFile = dbPropertiesFile;
            this.lockFile = lockFile;
        }

        @Override
        public File getDbPropertiesFile() {
            return dbPropertiesFile;
        }

        @Override
        public InstallAction getAction() {
            return new InstallAction() {
                @Override
                public void installSuccess() {
                    installSuccessCalled = true;
                }

                @Override
                public File getLockFile() {
                    return lockFile;
                }
            };
        }
    }
}
