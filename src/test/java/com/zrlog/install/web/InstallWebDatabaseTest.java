package com.zrlog.install.web;

import com.zrlog.install.business.vo.InstallConfigVO;
import com.zrlog.install.support.TestDatabase;
import com.zrlog.install.web.controller.api.ApiMigrateController;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static com.zrlog.install.web.InstallWebLayerTest.delete;
import static com.zrlog.install.web.InstallWebLayerTest.installConfig;
import static com.zrlog.install.web.InstallWebLayerTest.installParams;
import static com.zrlog.install.web.InstallWebLayerTest.request;
import static com.zrlog.install.web.InstallWebLayerTest.restoreProperty;
import static com.zrlog.install.web.InstallWebLayerTest.setControllerRequest;
import static com.zrlog.install.web.InstallWebLayerTest.setControllerResponse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class InstallWebDatabaseTest {

    @Parameterized.Parameters(name = "{0}")
    public static TestDatabase[] databases() {
        return TestDatabase.values();
    }

    private final TestDatabase database;

    public InstallWebDatabaseTest(TestDatabase database) {
        this.database = database;
    }

    @Test
    public void shouldWriteInstallCompleteEventWhenSseInstallSucceeds() throws Exception {
        Path confPath = Files.createTempDirectory("zrlog-install-controller-sse-success");
        String previousConfPath = System.getProperty("sws.conf.path");
        com.zrlog.install.web.config.InstallConfig previousConfig = InstallConstants.installConfig;
        try {
            System.setProperty("sws.conf.path", confPath.toString());
            InstallConstants.installConfig = installConfig(false, false);
            InstallWebLayerTest.TestApiInstallController controller =
                    new InstallWebLayerTest.TestApiInstallController();
            InstallWebLayerTest.CapturedResponse capturedResponse = new InstallWebLayerTest.CapturedResponse();
            setControllerRequest(controller, request("/api/install/start", new HashMap<>(),
                    Map.of("Accept", "text/event-stream")));
            setControllerResponse(controller, capturedResponse.response());
            InstallConfigVO configVO = new InstallConfigVO();
            configVO.setConfigMsg(installParams(database.name().toLowerCase()));
            configVO.setDbConfig(database.create(confPath.toFile(), "zrlog_install_web").asMap());
            configVO.setContextPath("/blog");

            controller.installStream(configVO);

            String body = new String(capturedResponse.written.readAllBytes());
            assertTrue(body, body.contains("event: install-progress"));
            assertTrue(body, body.contains("event: install-complete"));
            assertTrue(body, body.contains("\"content\""));
        } finally {
            InstallConstants.installConfig = previousConfig;
            restoreProperty("sws.conf.path", previousConfPath);
            delete(confPath);
        }
    }

    @Test
    public void shouldImportConvertedSqlIntoConfiguredDatabase() throws Exception {
        Path confPath = Files.createTempDirectory("zrlog-install-migrate-import");
        String previousConfPath = System.getProperty("sws.conf.path");
        TestDatabase.Configuration databaseConfig = database.create(confPath.toFile(), "zrlog_install_migrate");
        try {
            System.setProperty("sws.conf.path", confPath.toString());
            Files.writeString(confPath.resolve("mysql.sql"), ""
                    + "DROP TABLE IF EXISTS `legacy_a`, `legacy_b`;\n"
                    + "CREATE TABLE `log` (`id` int(11), `title` varchar(255));\n"
                    + "INSERT INTO `log` VALUES (1,'hello');\n"
                    + "INSERT INTO `log` VALUES (2,'world');\n");
            databaseConfig.writeProperties(confPath.resolve("sqlite-db.properties").toFile());
            ApiMigrateController controller = new ApiMigrateController();
            InstallWebLayerTest.CapturedResponse capturedResponse = new InstallWebLayerTest.CapturedResponse();
            setControllerRequest(controller, request("/api/migrate/import", new HashMap<>(), Map.of()));
            setControllerResponse(controller, capturedResponse.response());

            controller.doImportSqlite();

            assertNotNull(capturedResponse.json);
            try (Connection connection = databaseConfig.openConnection();
                 Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("select count(*) from `log`")) {
                assertTrue(resultSet.next());
                assertEquals(2, resultSet.getInt(1));
            }
        } finally {
            restoreProperty("sws.conf.path", previousConfPath);
            delete(confPath);
        }
    }
}
