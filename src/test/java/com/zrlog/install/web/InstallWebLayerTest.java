package com.zrlog.install.web;

import com.google.gson.Gson;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.impl.HttpRequestDecoderImpl;
import com.hibegin.http.server.impl.SimpleHttpRequest;
import com.hibegin.http.server.web.Controller;
import com.hibegin.http.server.web.MethodInterceptor;
import com.zrlog.install.business.type.TestConnectDbResult;
import com.zrlog.install.business.response.InstallProbeResponse;
import com.zrlog.install.business.response.InstallResourceResponse;
import com.zrlog.install.business.response.InstallResultResponse;
import com.zrlog.install.business.response.InstallRuntimeResourceResponse;
import com.zrlog.install.business.response.InstallUpgradeResult;
import com.zrlog.install.business.service.InstallOperationLock;
import com.zrlog.install.business.service.InstallOperationLockProcess;
import com.zrlog.install.business.service.InstallUpgradeAction;
import com.zrlog.install.business.vo.InstallConfigVO;
import com.zrlog.install.business.vo.InstallDatabaseConfig;
import com.zrlog.install.exception.AbstractInstallException;
import com.zrlog.install.exception.MissingDbHostException;
import com.zrlog.install.exception.MissingDbNameException;
import com.zrlog.install.exception.MissingDbPortException;
import com.zrlog.install.exception.MissingDbUserNameException;
import com.zrlog.install.exception.InstallException;
import com.zrlog.install.exception.InstallOperationInProgressException;
import com.zrlog.install.exception.InvalidInstallRequestException;
import com.zrlog.install.exception.InstalledException;
import com.zrlog.install.util.InstallNativeImageResourceUtils;
import com.zrlog.install.util.InstallSseEmitter;
import com.zrlog.install.util.LogCaptureSupport;
import com.zrlog.install.web.InstallConstants;
import com.zrlog.install.web.config.DefaultInstallConfig;
import com.zrlog.install.web.config.InstallRouters;
import com.zrlog.install.web.config.InstallServerConfig;
import com.zrlog.install.web.controller.api.ApiInstallController;
import com.zrlog.install.web.controller.api.ApiMigrateController;
import com.zrlog.install.web.interceptor.BlogInstallInterceptor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class InstallWebLayerTest {

    @Test
    public void shouldConfigureInstallRoutersAndServerConfig() {
        ServerConfig serverConfig = new ServerConfig();

        InstallRouters.configRouter(serverConfig);
        InstallServerConfig installServerConfig = new InstallServerConfig();

        assertTrue(serverConfig.getStaticResourceMapper().containsKey("/install/static/"));
        assertEquals(Integer.valueOf(6080), installServerConfig.getServerConfig().getPort());
        assertTrue(installServerConfig.getServerConfig().getInterceptors().contains(BlogInstallInterceptor.class));
        assertTrue(installServerConfig.getServerConfig().getInterceptors().contains(MethodInterceptor.class));
        assertNotNull(installServerConfig.getRequestConfig());
        assertNotNull(installServerConfig.getResponseConfig());
    }

    @Test
    public void shouldWriteSsePayloadsAndHeaders() throws Exception {
        CapturedResponse capturedResponse = new CapturedResponse();

        InstallSseEmitter.write(capturedResponse.response(), "install-test", "install-error",
                emitter -> emitter.send("install-progress", Map.of("status", "running")));

        String body = new String(capturedResponse.written.readAllBytes());
        assertEquals("text/event-stream;charset=UTF-8", capturedResponse.headers.get("Content-Type"));
        assertEquals("no-cache", capturedResponse.addedHeaders.get("Cache-Control"));
        assertEquals("keep-alive", capturedResponse.addedHeaders.get("Connection"));
        assertEquals("no", capturedResponse.addedHeaders.get("X-Accel-Buffering"));
        assertTrue(body.contains("event: install-progress"));
        assertTrue(body.contains("\"status\":\"running\""));
    }

    @Test
    public void shouldWriteSseErrorWhenWriterFails() throws Exception {
        CapturedResponse capturedResponse = new CapturedResponse();

        try (LogCaptureSupport logs = LogCaptureSupport.capture(InstallSseEmitter.class)) {
            InstallSseEmitter.write(capturedResponse.response(), "install-test", "install-error",
                    emitter -> {
                        throw new IllegalStateException("sse-do-not-expose");
                    });

            String body = new String(capturedResponse.written.readAllBytes());
            assertTrue(body.contains("event: install-error"));
            assertFalse(body.contains("do-not-expose"));
            assertTrue(body.contains("\"code\":\"INSTALL_STREAM_FAILED\""));
            assertTrue(body.contains("\"status\":\"error\""));
            assertTrue(body.contains("\"message\":"));
            assertTrue(logs.text().contains("phase=event-stream"));
            assertTrue(logs.text().contains("exception=java.lang.IllegalStateException"));
            assertFalse(logs.text().contains("do-not-expose"));
            assertFalse(logs.hasThrown());
        }
    }

    @Test
    public void shouldFailClosedForUncontrolledInstallExceptionInStream() throws Exception {
        com.zrlog.install.web.config.InstallConfig previousConfig = InstallConstants.installConfig;
        String sensitiveMarker = "jdbc:mysql://db/zrlog?password=sse-do-not-expose";
        try {
            InstallConstants.installConfig = new DefaultInstallConfig() {
                @Override
                public InstallAction getAction() {
                    return new InstallAction() {
                        @Override
                        public void installSuccess() {
                        }

                        @Override
                        public java.io.File getLockFile() {
                            return new java.io.File("install.lock");
                        }

                        @Override
                        public boolean isInstalled() {
                            throw new AbstractInstallException() {
                                @Override
                                public int getError() {
                                    return 7777;
                                }

                                @Override
                                public String getMessage() {
                                    return sensitiveMarker;
                                }
                            };
                        }
                    };
                }
            };
            TestApiInstallController controller = new TestApiInstallController();
            CapturedResponse capturedResponse = new CapturedResponse();
            setControllerResponse(controller, capturedResponse.response());

            try (LogCaptureSupport logs = LogCaptureSupport.capture(InstallSseEmitter.class)) {
                controller.installStream(new InstallConfigVO());

                String body = new String(capturedResponse.written.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(body, body.contains("event: install-error"));
                assertTrue(body, body.contains("\"code\":\"INSTALL_STREAM_FAILED\""));
                assertFalse(body, body.contains("do-not-expose"));
                assertFalse(body, body.contains("jdbc:mysql"));
                assertFalse(logs.text().contains("do-not-expose"));
                assertFalse(logs.hasThrown());
            }
        } finally {
            InstallConstants.installConfig = previousConfig;
        }
    }

    @Test
    public void shouldBuildDatabaseConnectionMapFromControllerRequest() throws Exception {
        TestApiInstallController controller = new TestApiInstallController();
        setControllerRequest(controller, jsonRequest("/api/install/testDbConn", Map.of(
                "dbHost", "localhost",
                "dbPort", "3306",
                "dbUserName", "root",
                "dbPassword", "password",
                "dbName", "zrlog",
                "dbType", "mysql"
        )));

        InstallDatabaseConfig dbConn = controller.dbConn();

        assertEquals("root", dbConn.getUser());
        assertEquals("password", dbConn.getPassword());
        assertEquals("mysql", dbConn.getDbType());
        assertEquals("localhost", dbConn.getDbHost());
        assertEquals("3306", dbConn.getDbPort());
        assertEquals("zrlog", dbConn.getDbName());
        assertEquals("com.mysql.cj.jdbc.Driver", dbConn.getDriverClass());
        assertTrue(dbConn.getJdbcUrl().contains("jdbc:mysql://localhost:3306/zrlog"));
    }

    @Test
    public void shouldBuildDatabaseConnectionFromJsonBodyWithoutQueryCredentials() throws Exception {
        TestApiInstallController controller = new TestApiInstallController();
        setControllerRequest(controller, jsonRequest("/api/install/testDbConn", Map.of(
                "dbHost", "database.internal",
                "dbPort", "3307",
                "dbUserName", "zrlog-user",
                "dbPassword", "body-only-secret",
                "dbName", "zrlog",
                "dbType", "mysql"
        )));

        InstallDatabaseConfig dbConn = controller.dbConn();

        assertEquals(HttpMethod.POST, controller.getRequest().getMethod());
        assertEquals("zrlog-user", dbConn.getUser());
        assertEquals("body-only-secret", dbConn.getPassword());
        assertEquals("database.internal", dbConn.getDbHost());
        assertEquals("3307", dbConn.getDbPort());
        assertTrue(controller.getRequest().getParamMap().isEmpty());
    }

    @Test
    public void shouldExcludeEveryPasswordFieldFromJdbcUrlQueryParameters() throws Exception {
        com.zrlog.install.web.config.InstallConfig previousConfig = InstallConstants.installConfig;
        AtomicReference<Map<String, String[]>> capturedParameters = new AtomicReference<>();
        try {
            InstallConstants.installConfig = new DefaultInstallConfig() {
                @Override
                public String getJdbcUrlQueryStr(String dbType, Map<String, String[]> paramMap) {
                    capturedParameters.set(new LinkedHashMap<>(paramMap));
                    return "safeOption=" + paramMap.get("safeOption")[0];
                }
            };
            Map<String, String> parameters = installParams("mysql");
            parameters.put("dbPassword", "database-secret-value");
            parameters.put("password", "admin-secret-value");
            parameters.put("confirmPassword", "admin-secret-value");
            parameters.put("proxyPassword", "proxy-secret-value");
            parameters.put("PASSWORD", "uppercase-secret-value");
            parameters.put("safeOption", "enabled");
            TestApiInstallController controller = new TestApiInstallController();
            setControllerRequest(controller, jsonRequest("/api/install/testDbConn", parameters));

            InstallDatabaseConfig dbConn = controller.dbConn();

            assertNotNull(capturedParameters.get());
            assertEquals("enabled", capturedParameters.get().get("safeOption")[0]);
            assertFalse(capturedParameters.get().keySet().stream()
                    .anyMatch(key -> key.toLowerCase(java.util.Locale.ROOT).contains("password")));
            assertEquals("database-secret-value", dbConn.getPassword());
            assertTrue(dbConn.getJdbcUrl().endsWith("?safeOption=enabled"));
            assertFalse(dbConn.getJdbcUrl().contains("database-secret-value"));
            assertFalse(dbConn.getJdbcUrl().contains("admin-secret-value"));
            assertFalse(dbConn.getJdbcUrl().contains("proxy-secret-value"));
            assertFalse(dbConn.getJdbcUrl().contains("uppercase-secret-value"));
        } finally {
            InstallConstants.installConfig = previousConfig;
        }
    }

    @Test
    public void shouldReadJsonBodyDecodedBySimpleWebServer() throws Exception {
        String body = new Gson().toJson(Map.of(
                "dbHost", "database.internal",
                "dbPort", "3306",
                "dbUserName", "zrlog-user",
                "dbPassword", "decoder-secret",
                "dbName", "zrlog",
                "dbType", "mysql"
        ));
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        String head = "POST /api/install/testDbConn HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Content-Type: application/json;charset=UTF-8\r\n"
                + "Content-Length: " + bodyBytes.length + "\r\n\r\n";
        byte[] headBytes = head.getBytes(StandardCharsets.UTF_8);
        ByteBuffer rawRequest = ByteBuffer.allocate(headBytes.length + bodyBytes.length);
        rawRequest.put(headBytes).put(bodyBytes).flip();
        ServerConfig serverConfig = new ServerConfig().setPort(6080);
        RequestConfig requestConfig = new RequestConfig();
        requestConfig.setMaxRequestBodySize(1024 * 1024);
        HttpRequestDecoderImpl decoder = new HttpRequestDecoderImpl(
                requestConfig, new ApplicationContext(serverConfig), null);
        decoder.doDecode(rawRequest);
        HttpRequest decodedRequest = decoder.getRequest();
        try {
            TestApiInstallController controller = new TestApiInstallController();
            setControllerRequest(controller, decodedRequest);

            InstallDatabaseConfig dbConn = controller.dbConn();

            assertEquals(HttpMethod.POST, decodedRequest.getMethod());
            assertTrue(decodedRequest.getParamMap().isEmpty());
            assertEquals("zrlog-user", dbConn.getUser());
            assertEquals("decoder-secret", dbConn.getPassword());
        } finally {
            ((SimpleHttpRequest) decodedRequest).deleteTempUploadFiles();
        }
    }

    @Test
    public void shouldRejectMalformedJsonWithStableErrorCode() throws Exception {
        TestApiInstallController controller = new TestApiInstallController();
        setControllerRequest(controller, jsonRequest("/api/install/testDbConn", "{invalid"));

        InvalidInstallRequestException exception = assertThrows(
                InvalidInstallRequestException.class, controller::dbConn);

        assertEquals(9025, exception.getError());
        assertEquals("INVALID_INSTALL_REQUEST", exception.getCode());
    }

    @Test
    public void shouldRejectMutationEndpointsWithoutSecurePostJsonContract() throws Exception {
        ApiInstallController testController = new ApiInstallController();
        setControllerRequest(testController, request(
                "/api/install/testDbConn", Map.of("dbType", "sqlite")));
        InvalidInstallRequestException testException = assertThrows(
                InvalidInstallRequestException.class, testController::testDbConn);
        assertEquals("INVALID_INSTALL_REQUEST", testException.getCode());

        ApiInstallController installController = new ApiInstallController();
        setControllerRequest(installController, request(
                "/api/install/startInstall", sqliteInstallParams(), Map.of(
                        "Content-Type", "application/x-www-form-urlencoded"),
                false, HttpMethod.POST, null));
        InvalidInstallRequestException installException = assertThrows(
                InvalidInstallRequestException.class, installController::startInstall);
        assertEquals("INVALID_INSTALL_REQUEST", installException.getCode());

        ApiInstallController upgradeController = new ApiInstallController();
        setControllerRequest(upgradeController, request(
                "/api/install/startUpgrade", Map.of(), Map.of(
                        "Content-Type", "application/jsonp"), false, HttpMethod.POST, "{}"));
        InvalidInstallRequestException upgradeException = assertThrows(
                InvalidInstallRequestException.class, upgradeController::startUpgrade);
        assertEquals("INVALID_INSTALL_REQUEST", upgradeException.getCode());
    }

    @Test
    public void shouldNeverReadCredentialsFromQueryWhenJsonBodyIsEmpty() throws Exception {
        ApiInstallController controller = new ApiInstallController();
        setControllerRequest(controller, request(
                "/api/install/startInstall", installParams("mysql"), Map.of(
                        "Content-Type", "application/json"),
                false, HttpMethod.POST, ""));

        InvalidInstallRequestException exception = assertThrows(
                InvalidInstallRequestException.class, controller::startInstall);

        assertEquals("INVALID_INSTALL_REQUEST", exception.getCode());
    }

    @Test
    public void shouldBuildServerOwnedSqliteConnectionWithoutNetworkParams() throws Exception {
        Path root = Files.createTempDirectory("zrlog-install-sqlite-config");
        com.zrlog.install.web.config.InstallConfig previousConfig = InstallConstants.installConfig;
        try {
            InstallConstants.installConfig = installConfigWithDbProperties(root.resolve("conf/db.properties"), false);
            TestApiInstallController controller = new TestApiInstallController();
            setControllerRequest(controller, jsonRequest(
                    "/api/install/testDbConn", Map.of("dbType", "sqlite")));

            InstallDatabaseConfig dbConn = controller.dbConn();

            assertEquals("sqlite", dbConn.getDbType());
            assertEquals("org.sqlite.JDBC", dbConn.getDriverClass());
            assertEquals("", dbConn.getUser());
            assertEquals("", dbConn.getPassword());
            assertEquals("zrlog", dbConn.getDbName());
            assertEquals(null, dbConn.getDbHost());
            assertEquals(null, dbConn.getDbPort());
            assertTrue(dbConn.getJdbcUrl().startsWith("jdbc:sqlite:"));
            assertTrue(dbConn.getJdbcUrl().contains(root.resolve("conf/zrlog.db").toAbsolutePath().toString()));
            assertTrue(dbConn.getJdbcUrl().contains("journal_mode=WAL"));
            assertTrue(dbConn.getJdbcUrl().contains("busy_timeout=10000"));
            assertTrue(dbConn.getJdbcUrl().contains("foreign_keys=on"));
            assertTrue(dbConn.getJdbcUrl().contains("date_class=TEXT"));
            assertTrue(dbConn.getJdbcUrl().contains("date_string_format=yyyy-MM-dd HH:mm:ss"));
        } finally {
            InstallConstants.installConfig = previousConfig;
            delete(root);
        }
    }

    @Test
    public void shouldRejectSqliteConnectionInWarMode() throws Exception {
        Path root = Files.createTempDirectory("zrlog-install-war-sqlite");
        com.zrlog.install.web.config.InstallConfig previousConfig = InstallConstants.installConfig;
        try {
            InstallConstants.installConfig = installConfigWithDbProperties(root.resolve("WEB-INF/db.properties"), true);
            TestApiInstallController controller = new TestApiInstallController();
            setControllerRequest(controller, jsonRequest(
                    "/api/install/testDbConn", Map.of("dbType", "sqlite")));

            InstallException exception = assertThrows(InstallException.class, controller::dbConn);

            assertEquals(TestConnectDbResult.UNSUPPORTED_DATABASE.name(), exception.getCode());
        } finally {
            InstallConstants.installConfig = previousConfig;
            delete(root);
        }
    }

    @Test
    public void shouldInstallWithServerOwnedSqliteThroughApi() throws Exception {
        Path root = Files.createTempDirectory("zrlog-install-api-sqlite");
        com.zrlog.install.web.config.InstallConfig previousConfig = InstallConstants.installConfig;
        try {
            Path dbProperties = root.resolve("conf/db.properties");
            InstallConstants.installConfig = installConfigWithDbProperties(dbProperties, false);
            ApiInstallController controller = new ApiInstallController();
            CapturedResponse capturedResponse = new CapturedResponse();
            Map<String, String> params = installParams("sqlite");
            params.keySet().removeAll(java.util.Set.of(
                    "dbHost", "dbPort", "dbUserName", "dbPassword", "dbName"));
            setControllerRequest(controller, jsonRequest("/api/install/startInstall", params));
            setControllerResponse(controller, capturedResponse.response());

            controller.startInstall();

            assertTrue(Files.exists(dbProperties));
            assertTrue(Files.exists(root.resolve("conf/install.lock")));
            assertTrue(Files.exists(root.resolve("conf/zrlog.db")));
            assertTrue(capturedResponse.json instanceof InstallResultResponse);
        } finally {
            InstallConstants.installConfig = previousConfig;
            delete(root);
        }
    }

    @Test
    public void shouldKeepSqliteConnectionTestIsolatedBeforeInstalling() throws Exception {
        Path root = Files.createTempDirectory("zrlog-install-api-sqlite-test");
        Path dbProperties = root.resolve("missing/conf/db.properties");
        com.zrlog.install.web.config.InstallConfig previousConfig = InstallConstants.installConfig;
        try {
            InstallConstants.installConfig = installConfigWithDbProperties(dbProperties, false);
            ApiInstallController controller = new ApiInstallController();
            setControllerRequest(controller, jsonRequest(
                    "/api/install/testDbConn", Map.of("dbType", "sqlite")));

            assertFalse(Files.exists(dbProperties.getParent()));
            assertEquals("pass", controller.probe().getData().getStatus());
            controller.testDbConn();

            assertTrue(Files.isDirectory(dbProperties.getParent()));
            Path databaseFile = dbProperties.resolveSibling("zrlog.db");
            assertFalse(Files.exists(databaseFile));
            try (Stream<Path> files = Files.list(dbProperties.getParent())) {
                assertFalse(files.anyMatch(path -> {
                    String name = path.getFileName().toString();
                    return name.startsWith(".zrlog-sqlite-probe-")
                            || name.startsWith("zrlog.db-");
                }));
            }

            ApiInstallController installController = new ApiInstallController();
            CapturedResponse capturedResponse = new CapturedResponse();
            Map<String, String> params = sqliteInstallParams();
            setControllerRequest(installController, jsonRequest("/api/install/startInstall", params));
            setControllerResponse(installController, capturedResponse.response());
            installController.startInstall();

            assertTrue(Files.isRegularFile(databaseFile));
            assertTrue(Files.isRegularFile(dbProperties));
            assertTrue(Files.isRegularFile(dbProperties.resolveSibling("install.lock")));
            assertTrue(capturedResponse.json instanceof InstallResultResponse);
        } finally {
            InstallConstants.installConfig = previousConfig;
            delete(root);
        }
    }

    @Test
    public void shouldRejectConcurrentInstallForJsonAndSseRequests() throws Exception {
        Path root = Files.createTempDirectory("zrlog-install-api-concurrent");
        Path dbProperties = root.resolve("conf/db.properties");
        CountDownLatch installActionEntered = new CountDownLatch(1);
        CountDownLatch finishInstall = new CountDownLatch(1);
        AtomicBoolean upgradeInvoked = new AtomicBoolean(false);
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        com.zrlog.install.web.config.InstallConfig previousConfig = InstallConstants.installConfig;
        Thread firstInstall = null;
        try {
            InstallAction action = new InstallAction() {
                @Override
                public void installSuccess() {
                    installActionEntered.countDown();
                    try {
                        finishInstall.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                @Override
                public java.io.File getLockFile() {
                    return dbProperties.resolveSibling("install.lock").toFile();
                }

                @Override
                public boolean isInstalled() {
                    return false;
                }
            };
            InstallConstants.installConfig = new DefaultInstallConfig() {
                @Override
                public InstallAction getAction() {
                    return action;
                }

                @Override
                public java.io.File getDbPropertiesFile() {
                    return dbProperties.toFile();
                }

                @Override
                public boolean isAskConfig() {
                    return false;
                }

                @Override
                public InstallUpgradeAction getUpgradeAction() {
                    return new InstallUpgradeAction() {
                        @Override
                        public boolean isSupported() {
                            return true;
                        }

                        @Override
                        public InstallUpgradeResult upgrade(ProgressListener progressListener) {
                            upgradeInvoked.set(true);
                            return new InstallUpgradeResult(true, "Updated");
                        }
                    };
                }
            };
            Map<String, String> params = sqliteInstallParams();
            ApiInstallController firstController = new ApiInstallController();
            CapturedResponse firstResponse = new CapturedResponse();
            setControllerRequest(firstController, jsonRequest("/api/install/startInstall", params));
            setControllerResponse(firstController, firstResponse.response());
            firstInstall = new Thread(() -> {
                try {
                    firstController.startInstall();
                } catch (Throwable e) {
                    firstFailure.set(e);
                }
            }, "first-install-test");
            firstInstall.start();
            assertTrue("First installation did not reach install action",
                    installActionEntered.await(10, TimeUnit.SECONDS));

            ApiInstallController jsonController = new ApiInstallController();
            setControllerRequest(jsonController, jsonRequest("/api/install/startInstall", params));
            setControllerResponse(jsonController, new CapturedResponse().response());
            InstallOperationInProgressException exception = assertThrows(
                    InstallOperationInProgressException.class, jsonController::startInstall);
            assertEquals(9024, exception.getError());
            assertEquals("INSTALL_IN_PROGRESS", exception.getCode());

            ApiInstallController sseController = new ApiInstallController();
            CapturedResponse sseResponse = new CapturedResponse();
            setControllerRequest(sseController, jsonRequest(
                    "/api/install/startInstall", params, Map.of("Accept", "text/event-stream")));
            setControllerResponse(sseController, sseResponse.response());
            sseController.startInstall();
            String sseBody = new String(sseResponse.written.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(sseBody, sseBody.contains("event: install-error"));
            assertTrue(sseBody, sseBody.contains("\"code\":\"INSTALL_IN_PROGRESS\""));
            assertTrue(sseBody, sseBody.contains("\"status\":\"error\""));

            TestApiInstallController upgradeController = new TestApiInstallController();
            CapturedResponse upgradeResponse = new CapturedResponse();
            setControllerResponse(upgradeController, upgradeResponse.response());
            upgradeController.upgradeStream();
            String upgradeBody = new String(
                    upgradeResponse.written.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(upgradeBody, upgradeBody.contains("event: upgrade-error"));
            assertTrue(upgradeBody, upgradeBody.contains("\"code\":\"INSTALL_IN_PROGRESS\""));
            assertFalse(upgradeInvoked.get());

            finishInstall.countDown();
            firstInstall.join(10000);
            assertFalse("First installation thread is still running", firstInstall.isAlive());
            assertEquals(null, firstFailure.get());
            assertTrue(firstResponse.json instanceof InstallResultResponse);
        } finally {
            finishInstall.countDown();
            if (firstInstall != null) {
                firstInstall.join(10000);
            }
            InstallConstants.installConfig = previousConfig;
            delete(root);
        }
    }

    @Test
    public void shouldRejectInstallWhileUpgradeIsRunningAndReleaseOperationLock() throws Exception {
        Path root = Files.createTempDirectory("zrlog-upgrade-install-concurrent");
        Path dbProperties = root.resolve("conf/db.properties");
        CountDownLatch upgradeEntered = new CountDownLatch(1);
        CountDownLatch finishUpgrade = new CountDownLatch(1);
        String previousConfPath = System.getProperty("sws.conf.path");
        com.zrlog.install.web.config.InstallConfig previousConfig = InstallConstants.installConfig;
        CapturedResponse upgradeResponse = new CapturedResponse();
        try {
            System.setProperty("sws.conf.path", dbProperties.getParent().toString());
            Files.createDirectories(dbProperties.getParent());
            Files.writeString(dbProperties.getParent().resolve("mysql.sql"),
                    "CREATE TABLE `log` (`id` int(11), `title` varchar(255));\n");
            InstallConstants.installConfig = new DefaultInstallConfig() {
                @Override
                public InstallAction getAction() {
                    return new InstallAction() {
                        @Override
                        public void installSuccess() {
                        }

                        @Override
                        public java.io.File getLockFile() {
                            return dbProperties.resolveSibling("install.lock").toFile();
                        }

                        @Override
                        public boolean isInstalled() {
                            return false;
                        }
                    };
                }

                @Override
                public java.io.File getDbPropertiesFile() {
                    return dbProperties.toFile();
                }

                @Override
                public boolean isAskConfig() {
                    return false;
                }

                @Override
                public InstallUpgradeAction getUpgradeAction() {
                    return new InstallUpgradeAction() {
                        @Override
                        public boolean isSupported() {
                            return true;
                        }

                        @Override
                        public InstallUpgradeResult upgrade(ProgressListener progressListener) throws Exception {
                            upgradeEntered.countDown();
                            finishUpgrade.await();
                            return new InstallUpgradeResult(true, "Updated");
                        }
                    };
                }
            };
            TestApiInstallController upgradeController = new TestApiInstallController();
            setControllerResponse(upgradeController, upgradeResponse.response());
            upgradeController.upgradeStream();
            assertTrue("Upgrade did not acquire operation lock",
                    upgradeEntered.await(10, TimeUnit.SECONDS));

            ApiInstallController installController = new ApiInstallController();
            setControllerRequest(installController, jsonRequest(
                    "/api/install/startInstall", sqliteInstallParams()));
            setControllerResponse(installController, new CapturedResponse().response());
            InstallOperationInProgressException exception = assertThrows(
                    InstallOperationInProgressException.class, installController::startInstall);
            assertEquals("UPGRADE_IN_PROGRESS", exception.getCode());

            ApiMigrateController migrateController = new ApiMigrateController();
            setControllerRequest(migrateController, migrationRequest("/api/migrate/convert"));
            InstallOperationInProgressException migrateException = assertThrows(
                    InstallOperationInProgressException.class,
                    migrateController::convertToSqliteSqlFile);
            assertEquals("INSTALL_IN_PROGRESS", migrateException.getCode());
            assertFalse(Files.exists(dbProperties.getParent().resolve("sqlite.sql")));

            finishUpgrade.countDown();
            String upgradeBody = new String(
                    upgradeResponse.written.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(upgradeBody, upgradeBody.contains("event: upgrade-complete"));

            ApiMigrateController retryMigrateController = new ApiMigrateController();
            CapturedResponse migrateResponse = new CapturedResponse();
            setControllerRequest(retryMigrateController, migrationRequest("/api/migrate/convert"));
            setControllerResponse(retryMigrateController, migrateResponse.response());
            retryMigrateController.convertToSqliteSqlFile();
            assertNotNull(migrateResponse.json);
            assertTrue(Files.exists(dbProperties.getParent().resolve("sqlite.sql")));

            ApiInstallController retryController = new ApiInstallController();
            CapturedResponse retryResponse = new CapturedResponse();
            setControllerRequest(retryController, jsonRequest(
                    "/api/install/startInstall", sqliteInstallParams()));
            setControllerResponse(retryController, retryResponse.response());
            retryController.startInstall();
            assertTrue(retryResponse.json instanceof InstallResultResponse);
        } finally {
            finishUpgrade.countDown();
            if (upgradeResponse.written != null) {
                upgradeResponse.written.readAllBytes();
            }
            InstallConstants.installConfig = previousConfig;
            restoreProperty("sws.conf.path", previousConfPath);
            delete(root);
        }
    }

    @Test(timeout = 20000)
    public void shouldRejectUpgradeDuringExternalOperationWithoutLeakingPathAndReleaseState() throws Exception {
        Path root = Files.createTempDirectory("zrlog-upgrade-external-operation");
        Path dbProperties = root.resolve("conf/db.properties");
        Path installLock = dbProperties.resolveSibling("install.lock");
        Path readyFile = root.resolve("external.ready");
        Path releaseFile = root.resolve("external.release");
        AtomicBoolean upgradeInvoked = new AtomicBoolean(false);
        com.zrlog.install.web.config.InstallConfig previousConfig = InstallConstants.installConfig;
        Process externalLockHolder = null;
        try {
            InstallConstants.installConfig = new DefaultInstallConfig() {
                @Override
                public InstallAction getAction() {
                    return new InstallAction() {
                        @Override
                        public void installSuccess() {
                        }

                        @Override
                        public java.io.File getLockFile() {
                            return installLock.toFile();
                        }

                        @Override
                        public boolean isInstalled() {
                            return false;
                        }
                    };
                }

                @Override
                public InstallUpgradeAction getUpgradeAction() {
                    return new InstallUpgradeAction() {
                        @Override
                        public boolean isSupported() {
                            return true;
                        }

                        @Override
                        public InstallUpgradeResult upgrade(ProgressListener progressListener) {
                            upgradeInvoked.set(true);
                            return new InstallUpgradeResult(true, "Updated");
                        }
                    };
                }
            };
            externalLockHolder = startOperationLockHolder(
                    installLock.toFile(), readyFile, releaseFile);
            waitForOperationLock(externalLockHolder, readyFile);

            TestApiInstallController blockedController = new TestApiInstallController();
            CapturedResponse blockedResponse = new CapturedResponse();
            setControllerResponse(blockedController, blockedResponse.response());
            blockedController.upgradeStream();

            String blockedBody = new String(
                    blockedResponse.written.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(blockedBody, blockedBody.contains("event: upgrade-error"));
            assertTrue(blockedBody, blockedBody.contains("\"code\":\"INSTALL_IN_PROGRESS\""));
            assertFalse(blockedBody, blockedBody.contains("INSTALL_STREAM_FAILED"));
            assertFalse(blockedBody, blockedBody.contains(root.toAbsolutePath().toString()));
            assertFalse(blockedBody, blockedBody.contains(".install-operation.lock"));
            assertFalse(upgradeInvoked.get());

            Files.createFile(releaseFile);
            assertTrue("External lock holder did not exit",
                    externalLockHolder.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, externalLockHolder.exitValue());

            TestApiInstallController retryController = new TestApiInstallController();
            CapturedResponse retryResponse = new CapturedResponse();
            setControllerResponse(retryController, retryResponse.response());
            retryController.upgradeStream();
            String retryBody = new String(
                    retryResponse.written.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(retryBody, retryBody.contains("event: upgrade-complete"));
            assertTrue(upgradeInvoked.get());

            try (InstallOperationLock releasedLock = InstallOperationLock.tryAcquire(installLock.toFile())) {
                assertNotNull(releasedLock);
            }
        } finally {
            if (externalLockHolder != null && externalLockHolder.isAlive()) {
                externalLockHolder.destroyForcibly();
                externalLockHolder.waitFor(10, TimeUnit.SECONDS);
            }
            InstallConstants.installConfig = previousConfig;
            delete(root);
        }
    }

    @Test
    public void shouldRejectRepeatedInstallWithCompletedCode() throws Exception {
        com.zrlog.install.web.config.InstallConfig previousConfig = InstallConstants.installConfig;
        try {
            InstallConstants.installConfig = installConfig(true, false);
            ApiInstallController controller = new ApiInstallController();
            setControllerRequest(controller, jsonRequest(
                    "/api/install/startInstall", sqliteInstallParams()));
            setControllerResponse(controller, new CapturedResponse().response());

            InstalledException exception = assertThrows(InstalledException.class, controller::startInstall);

            assertEquals(9020, exception.getError());
            assertEquals("INSTALL_ALREADY_COMPLETED", exception.getCode());
        } finally {
            InstallConstants.installConfig = previousConfig;
        }
    }

    @Test
    public void shouldStreamCompletedCodeWhenAnotherProcessFinishesBeforeTheLockCheck() throws Exception {
        Path root = Files.createTempDirectory("zrlog-install-completed-lock-race");
        Path installLock = root.resolve("install.lock");
        AtomicInteger installedChecks = new AtomicInteger();
        InstallAction action = new InstallAction() {
            @Override
            public void installSuccess() {
            }

            @Override
            public java.io.File getLockFile() {
                return installLock.toFile();
            }

            @Override
            public boolean isInstalled() {
                return installedChecks.incrementAndGet() >= 3;
            }
        };
        com.zrlog.install.web.config.InstallConfig previousConfig = InstallConstants.installConfig;
        try {
            InstallConstants.installConfig = new DefaultInstallConfig() {
                @Override
                public InstallAction getAction() {
                    return action;
                }

                @Override
                public java.io.File getDbPropertiesFile() {
                    return root.resolve("db.properties").toFile();
                }
            };
            ApiInstallController controller = new ApiInstallController();
            CapturedResponse capturedResponse = new CapturedResponse();
            setControllerRequest(controller, jsonRequest(
                    "/api/install/startInstall", installParams("mysql"),
                    Map.of("Accept", "text/event-stream")));
            setControllerResponse(controller, capturedResponse.response());

            controller.startInstall();

            String body = new String(capturedResponse.written.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(body, body.contains("event: install-error"));
            assertTrue(body, body.contains("\"code\":\"INSTALL_ALREADY_COMPLETED\""));
            assertFalse(body, body.contains("Install failed"));
            assertEquals(3, installedChecks.get());
        } finally {
            InstallConstants.installConfig = previousConfig;
            delete(root);
        }
    }

    @Test
    public void shouldRenderInstallIndexWithRuntimeResourceInfo() throws Exception {
        com.zrlog.install.web.controller.page.InstallController controller =
                new com.zrlog.install.web.controller.page.InstallController();
        CapturedResponse capturedResponse = new CapturedResponse();
        setControllerRequest(controller, request("/install", new HashMap<>()));
        setControllerResponse(controller, capturedResponse.response());

        controller.index();

        assertNotNull(capturedResponse.html);
        Document document = Jsoup.parse(capturedResponse.html);
        assertEquals("ZrLog \u5b89\u88c5\u5411\u5bfc", document.title());
        assertEquals("/blog/", document.selectFirst("base").attr("href"));
        assertEquals("zh-CN", document.selectFirst("html").attr("lang"));
        assertFalse(document.body().hasClass("dark"));
        assertFalse(document.body().hasClass("light"));
        assertTrue(document.getElementById("resourceInfo").text().contains("\"feedbackUrl\""));
        assertTrue(document.getElementById("resourceInfo").text().contains("blog.zrlog.com"));
        assertTrue(document.getElementById("resourceInfo").text().contains("feedback.html"));
        assertTrue(document.head().select("link[rel=shortcut icon]").attr("href").startsWith("/blog/"));
    }

    @Test
    public void shouldRenderEnglishInstallIndexMetadata() throws Exception {
        com.zrlog.install.web.config.InstallConfig previousConfig = InstallConstants.installConfig;
        try {
            InstallConstants.installConfig = new DefaultInstallConfig() {
                @Override
                public String getAcceptLanguage() {
                    return "en_US";
                }
            };
            com.zrlog.install.web.controller.page.InstallController controller =
                    new com.zrlog.install.web.controller.page.InstallController();
            CapturedResponse capturedResponse = new CapturedResponse();
            setControllerRequest(controller, request("/install", new HashMap<>()));
            setControllerResponse(controller, capturedResponse.response());

            controller.index();

            Document document = Jsoup.parse(capturedResponse.html);
            assertEquals("ZrLog Setup", document.title());
            assertEquals("en", document.selectFirst("html").attr("lang"));
        } finally {
            InstallConstants.installConfig = previousConfig;
        }
    }

    @Test
    public void shouldExposeInstallResourceAndProbeResponses() throws Exception {
        ApiInstallController controller = new ApiInstallController();
        setControllerRequest(controller, request("/api/install/resource", new HashMap<>()));

        InstallResourceResponse resourceResponse = controller.installResource();
        assertTrue(resourceResponse.getData() instanceof InstallRuntimeResourceResponse);
        InstallRuntimeResourceResponse resourceData = (InstallRuntimeResourceResponse) resourceResponse.getData();
        assertEquals(InstallConstants.installConfig.getAcceptLanguage(), resourceData.getLang());
        assertNotNull(resourceData.getRuntimeMode());
        assertTrue(resourceData.getFeedbackUrl().contains("https://blog.zrlog.com/feedback.html?v="));

        InstallProbeResponse probeResponse = controller.probe();
        assertNotNull(probeResponse.getData());
        assertNotNull(probeResponse.getData().getRuntimeMode());
        assertNotNull(probeResponse.getData().getStatus());
        assertFalse(probeResponse.getData().getItems().isEmpty());
    }

    @Test
    public void shouldRecoverAskConfigCompletionOnlyThroughAuthorizedPost() throws Exception {
        Path root = Files.createTempDirectory("zrlog-install-completion");
        Path dbProperties = root.resolve("conf/db.properties");
        Files.createDirectories(dbProperties.getParent());
        Files.writeString(dbProperties, ""
                + "jdbcUrl=jdbc:mysql://database.internal:3306/zrlog\n"
                + "driverClass=com.mysql.cj.jdbc.Driver\n"
                + "user=zrlog_user\n"
                + "password=example-password\n", StandardCharsets.UTF_8);
        com.zrlog.install.web.config.InstallConfig previousConfig = InstallConstants.installConfig;
        try {
            InstallConstants.installConfig = completionConfig(dbProperties, true, true);

            ApiInstallController controller = new ApiInstallController();
            setControllerRequest(controller, jsonRequest(
                    "/api/install/installCompletion", "{}", Map.of(
                            InstallRequestSecurity.TOKEN_HEADER, "ignored-when-disabled")));
            InstallResultResponse result = controller.installCompletion();
            assertNotNull(result.getData());
            assertFalse(result.getData().getContent().isEmpty());

            BlogInstallInterceptor interceptor = new BlogInstallInterceptor();
            CapturedResponse routedResponse = new CapturedResponse();
            assertFalse(interceptor.doInterceptor(
                    routedJsonRequest("/api/install/installCompletion", Map.of()),
                    routedResponse.response()));
            assertTrue(routedResponse.json instanceof InstallResultResponse);
            assertEquals("no-store", routedResponse.headers.get("Cache-Control"));
            assertEquals("no-cache", routedResponse.headers.get("Pragma"));
        } finally {
            InstallConstants.installConfig = previousConfig;
            delete(root);
        }
    }

    @Test
    public void shouldRejectUnauthorizedOrInapplicableCompletionRecovery() throws Exception {
        Path root = Files.createTempDirectory("zrlog-install-completion-rejected");
        Path dbProperties = root.resolve("db.properties");
        Files.writeString(dbProperties, "jdbcUrl=jdbc:mysql://localhost:3306/zrlog\n",
                StandardCharsets.UTF_8);
        com.zrlog.install.web.config.InstallConfig previousConfig = InstallConstants.installConfig;
        try {
            InstallConstants.installConfig = completionConfig(dbProperties, true, true);
            ApiInstallController getController = new ApiInstallController();
            setControllerRequest(getController, request(
                    "/api/install/installCompletion", Map.of(), Map.of(
                            "Content-Type", "application/json"),
                    false, HttpMethod.GET, "{}"));
            assertThrows(InvalidInstallRequestException.class, getController::installCompletion);

            InstallConstants.installConfig = completionConfig(dbProperties, true, false);
            ApiInstallController nonAskConfigController = new ApiInstallController();
            setControllerRequest(nonAskConfigController, jsonRequest(
                    "/api/install/installCompletion", "{}"));
            assertThrows(InvalidInstallRequestException.class, nonAskConfigController::installCompletion);

            InstallConstants.installConfig = completionConfig(dbProperties, false, true);
            ApiInstallController incompleteInstallController = new ApiInstallController();
            setControllerRequest(incompleteInstallController, jsonRequest(
                    "/api/install/installCompletion", "{}"));
            assertThrows(InvalidInstallRequestException.class, incompleteInstallController::installCompletion);

            InstallConstants.installConfig = completionConfig(root.resolve("missing.properties"), true, true);
            ApiInstallController missingConfigController = new ApiInstallController();
            setControllerRequest(missingConfigController, jsonRequest(
                    "/api/install/installCompletion", "{}"));
            assertThrows(InvalidInstallRequestException.class, missingConfigController::installCompletion);
        } finally {
            InstallConstants.installConfig = previousConfig;
            delete(root);
        }
    }

    @Test
    public void shouldDetectSseRequestsByAcceptHeader() throws Exception {
        TestApiInstallController controller = new TestApiInstallController();
        setControllerRequest(controller, request("/api/install/start", new HashMap<>(),
                Map.of("Accept", "application/json, text/event-stream")));
        assertTrue(controller.sseRequest());

        setControllerRequest(controller, request("/api/install/start", new HashMap<>(),
                Map.of("Accept", "application/json")));
        assertFalse(controller.sseRequest());
    }

    @Test
    public void shouldRejectMissingDatabaseConnectionParams() throws Exception {
        assertInvocationCause(MissingDbHostException.class, Map.of());
        assertInvocationCause(MissingDbPortException.class, Map.of("dbHost", "localhost"));
        assertInvocationCause(MissingDbUserNameException.class, Map.of("dbHost", "localhost", "dbPort", "3306"));
        assertInvocationCause(MissingDbNameException.class,
                Map.of("dbHost", "localhost", "dbPort", "3306", "dbUserName", "root"));
    }

    @Test
    public void shouldRejectInstallApiEntrypointsWhenDatabaseParamsAreMissing() throws Exception {
        ApiInstallController testController = new ApiInstallController();
        setControllerRequest(testController, jsonRequest("/api/install/testDbConn", new HashMap<>()));

        assertThrows(MissingDbHostException.class, testController::testDbConn);

        ApiInstallController installController = new ApiInstallController();
        setControllerRequest(installController, jsonRequest("/api/install/startInstall", new HashMap<>()));
        setControllerResponse(installController, new CapturedResponse().response());

        assertThrows(MissingDbHostException.class, installController::startInstall);
    }

    @Test
    public void shouldRejectQueryStringsOnEveryInstallMutation() throws Exception {
        ApiInstallController testController = new ApiInstallController();
        setControllerRequest(testController, jsonRequest(
                "/api/install/testDbConn?dbPassword=visible", installParams("mysql")));
        assertThrows(InvalidInstallRequestException.class, testController::testDbConn);

        ApiInstallController installController = new ApiInstallController();
        setControllerRequest(installController, jsonRequest(
                "/api/install/startInstall?password=visible", installParams("mysql")));
        assertThrows(InvalidInstallRequestException.class, installController::startInstall);

        ApiInstallController recoveryController = new ApiInstallController();
        setControllerRequest(recoveryController, jsonRequest(
                "/api/install/resumeInstall?token=visible", "{}"));
        assertThrows(InvalidInstallRequestException.class, recoveryController::resumeInstall);

        ApiInstallController upgradeController = new ApiInstallController();
        setControllerRequest(upgradeController, jsonRequest(
                "/api/install/startUpgrade?token=visible", "{}"));
        assertThrows(InvalidInstallRequestException.class, upgradeController::startUpgrade);

        ApiInstallController completionController = new ApiInstallController();
        setControllerRequest(completionController, jsonRequest(
                "/api/install/installCompletion?token=visible", "{}"));
        assertThrows(InvalidInstallRequestException.class, completionController::installCompletion);
    }

    @Test
    public void shouldEnforceDatabaseAndSiteSemanticsAtControllerBoundary() throws Exception {
        Map<String, String> unsafeDatabase = installParams("mysql");
        unsafeDatabase.put("dbPort", "3306?allowMultiQueries=true");
        TestApiInstallController databaseController = new TestApiInstallController();
        setControllerRequest(databaseController, jsonRequest(
                "/api/install/testDbConn", unsafeDatabase));
        assertThrows(InvalidInstallRequestException.class, databaseController::dbConn);

        Map<String, String> weakAdmin = installParams("mysql");
        weakAdmin.put("password", "short");
        ApiInstallController installController = new ApiInstallController();
        setControllerRequest(installController, jsonRequest(
                "/api/install/startInstall", weakAdmin));
        setControllerResponse(installController, new CapturedResponse().response());
        assertThrows(InvalidInstallRequestException.class, installController::startInstall);
    }

    @Test
    public void shouldRejectUnsupportedDatabaseTypeBeforeConnectionTest() throws Exception {
        ApiInstallController controller = new ApiInstallController();
        setControllerRequest(controller, jsonRequest(
                "/api/install/testDbConn", installParams("unsupported")));

        InvalidInstallRequestException exception = assertThrows(
                InvalidInstallRequestException.class, controller::testDbConn);

        assertEquals(9025, exception.getError());
        assertEquals("INVALID_INSTALL_REQUEST", exception.getCode());
    }

    @Test
    public void shouldRejectUnsupportedDatabaseTypeBeforeNormalInstall() throws Exception {
        ApiInstallController controller = new ApiInstallController();
        setControllerRequest(controller, jsonRequest(
                "/api/install/startInstall", installParams("unsupported")));
        setControllerResponse(controller, new CapturedResponse().response());

        InvalidInstallRequestException exception = assertThrows(
                InvalidInstallRequestException.class, controller::startInstall);

        assertEquals("INVALID_INSTALL_REQUEST", exception.getCode());
    }

    @Test
    public void shouldRejectUnsupportedDatabaseTypeBeforeSseInstallStarts() throws Exception {
        ApiInstallController controller = new ApiInstallController();
        CapturedResponse capturedResponse = new CapturedResponse();
        setControllerRequest(controller, jsonRequest("/api/install/startInstall", installParams("unsupported"),
                Map.of("Accept", "text/event-stream")));
        setControllerResponse(controller, capturedResponse.response());

        assertThrows(InvalidInstallRequestException.class, controller::startInstall);
        assertEquals(null, capturedResponse.written);
    }

    @Test
    public void shouldWriteUpgradeProgressAndCompleteEventsBeforeInstallation() throws Exception {
        com.zrlog.install.web.config.InstallConfig previousConfig = InstallConstants.installConfig;
        try {
            InstallConstants.installConfig = new DefaultInstallConfig() {
                @Override
                public InstallUpgradeAction getUpgradeAction() {
                    return new InstallUpgradeAction() {
                        @Override
                        public boolean isSupported() {
                            return true;
                        }

                        @Override
                        public InstallUpgradeResult upgrade(ProgressListener progressListener) throws Exception {
                            progressListener.onProgress("upgrade-progress", Map.of(
                                    "stage", "download", "status", "complete", "message", "Downloaded"));
                            return new InstallUpgradeResult(true, "Updated");
                        }
                    };
                }
            };
            TestApiInstallController controller = new TestApiInstallController();
            CapturedResponse capturedResponse = new CapturedResponse();
            setControllerResponse(controller, capturedResponse.response());

            controller.upgradeStream();

            String body = new String(capturedResponse.written.readAllBytes());
            assertTrue(body, body.contains("event: upgrade-progress"));
            assertTrue(body, body.contains("\"stage\":\"download\""));
            assertTrue(body, body.contains("event: upgrade-complete"));
            assertTrue(body, body.contains("\"finish\":true"));
        } finally {
            InstallConstants.installConfig = previousConfig;
        }
    }

    @Test
    public void shouldBuildInstallResultResponseWhenAskConfigIsDisabled() throws Exception {
        com.zrlog.install.web.config.InstallConfig previousConfig = InstallConstants.installConfig;
        try {
            InstallConstants.installConfig = installConfig(false, false);
            TestApiInstallController controller = new TestApiInstallController();
            setControllerRequest(controller, request("/api/install/start", new HashMap<>()));

            InstallResultResponse response = controller.installResult();

            assertEquals(0, response.getError());
            assertNotNull(response.getData());
            assertEquals("", response.getData().getContent());
        } finally {
            InstallConstants.installConfig = previousConfig;
        }
    }

    @Test
    public void shouldDetectMigrateBatchDropTableSql() throws Exception {
        assertTrue(TestApiMigrateController.batchDropTableSql("DROP TABLE IF EXISTS `a`, `b`"));
        assertFalse(TestApiMigrateController.batchDropTableSql("DROP TABLE IF EXISTS `a`"));
    }

    @Test
    public void shouldDetectEveryMigrateDropSqlAfterLeadingTrivia() throws Exception {
        assertTrue(TestApiMigrateController.dropSql("DROP TABLE IF EXISTS `single_table`"));
        assertTrue(TestApiMigrateController.dropSql("  dRoP VIEW `legacy_view`"));
        assertTrue(TestApiMigrateController.dropSql("-- migration cleanup\nDROP INDEX `legacy_index`"));
        assertTrue(TestApiMigrateController.dropSql("# migration cleanup\r\nDROP TABLE `legacy_table`"));
        assertTrue(TestApiMigrateController.dropSql("/* migration cleanup */ DROP TABLE `legacy_table`"));
        assertTrue(TestApiMigrateController.dropSql("/* first */\n/* second */\nDROP TABLE `legacy_table`"));
        assertTrue(TestApiMigrateController.dropSql(
                "CREATE TABLE `staging` (`id` int); DROP TABLE `legacy_table`"));
        assertTrue(TestApiMigrateController.dropSql(
                "CREATE TABLE `staging` (`id` int);\r\nDROP TABLE `legacy_table`"));
        assertTrue(TestApiMigrateController.dropSql(
                "SELECT 'abc\\'; DROP TABLE `legacy_table`"));
        assertTrue(TestApiMigrateController.dropSql(
                "/*!50000 DROP TABLE `legacy_table` */"));
        assertFalse(TestApiMigrateController.dropSql("/* keep */ CREATE TABLE `log` (`id` int)"));
        assertFalse(TestApiMigrateController.dropSql(
                "INSERT INTO `log` VALUES ('DROP TABLE private_data')"));
        assertFalse(TestApiMigrateController.dropSql("CREATE TABLE `DROP` (`id` int)"));
        assertFalse(TestApiMigrateController.dropSql("DROPPER TABLE `not_a_keyword`"));
        assertFalse(TestApiMigrateController.dropSql("DROP_foo TABLE `not_a_keyword`"));
    }

    @Test
    public void shouldConvertMysqlSqlToSqliteFile() throws Exception {
        Path confPath = Files.createTempDirectory("zrlog-install-migrate");
        String previousConfPath = System.getProperty("sws.conf.path");
        try {
            System.setProperty("sws.conf.path", confPath.toString());
            Files.writeString(confPath.resolve("mysql.sql"), ""
                    + "DROP TABLE IF EXISTS `a`, `b`;\n"
                    + "DROP TABLE IF EXISTS `single_table`;\n"
                    + "/* migration cleanup */ dRoP TABLE IF EXISTS `commented_table`;\n"
                    + "CREATE TABLE `same_line` (`id` int); DROP TABLE IF EXISTS `same_line`;\n"
                    + "CREATE TABLE `crlf_line` (`id` int);\r\nDROP TABLE IF EXISTS `crlf_line`;\n"
                    + "CREATE TABLE `log` (`id` int(11) NOT NULL AUTO_INCREMENT, `title` varchar(255), PRIMARY KEY (`id`)) ENGINE=InnoDB DEFAULT CHARSET=utf8;\n"
                    + "INSERT INTO `log` VALUES (1,'hello'),(2,'world');\n");
            ApiMigrateController controller = new ApiMigrateController();
            CapturedResponse capturedResponse = new CapturedResponse();
            setControllerRequest(controller, migrationRequest("/api/migrate/convert"));
            setControllerResponse(controller, capturedResponse.response());

            controller.convertToSqliteSqlFile();

            String sqliteSql = Files.readString(confPath.resolve("sqlite.sql"));
            assertNotNull(capturedResponse.json);
            assertFalse(sqliteSql.contains("DROP TABLE IF EXISTS `a`, `b`"));
            assertFalse(sqliteSql.toUpperCase(Locale.ROOT).contains("DROP TABLE"));
            assertTrue(sqliteSql.contains("CREATE TABLE `log`"));
            assertTrue(sqliteSql.contains("INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT"));
            assertTrue(sqliteSql.contains("INSERT INTO `log` VALUES (1,'hello')"));
            assertTrue(sqliteSql.contains("INSERT INTO `log` VALUES (2,'world')"));
        } finally {
            restoreProperty("sws.conf.path", previousConfPath);
            delete(confPath);
        }
    }

    @Test(timeout = 10000)
    public void shouldCoordinateMigrationsWithInstallOperationLockAndReleaseIt() throws Exception {
        Path confPath = Files.createTempDirectory("zrlog-install-migrate-lock");
        String previousConfPath = System.getProperty("sws.conf.path");
        com.zrlog.install.web.config.InstallConfig previousConfig = InstallConstants.installConfig;
        try {
            System.setProperty("sws.conf.path", confPath.toString());
            InstallConstants.installConfig = installConfig(false, false);
            Files.writeString(confPath.resolve("mysql.sql"),
                    "CREATE TABLE `log` (`id` int(11), `title` varchar(255));\n");

            try (InstallOperationLock heldByInstall = InstallOperationLock.tryAcquire(
                    InstallConstants.installConfig.getAction().getLockFile())) {
                assertNotNull(heldByInstall);

                ApiMigrateController convertController = new ApiMigrateController();
                setControllerRequest(convertController, migrationRequest("/api/migrate/convert"));
                InstallOperationInProgressException convertConflict = assertThrows(
                        InstallOperationInProgressException.class,
                        convertController::convertToSqliteSqlFile);
                assertEquals(9024, convertConflict.getError());
                assertEquals("INSTALL_IN_PROGRESS", convertConflict.getCode());

                ApiMigrateController importController = new ApiMigrateController();
                setControllerRequest(importController, migrationRequest("/api/migrate/import"));
                InstallOperationInProgressException importConflict = assertThrows(
                        InstallOperationInProgressException.class, importController::doImportSqlite);
                assertEquals("INSTALL_IN_PROGRESS", importConflict.getCode());
                assertFalse(Files.exists(confPath.resolve("sqlite.sql")));
            }

            Files.delete(confPath.resolve("mysql.sql"));
            ApiMigrateController failedController = new ApiMigrateController();
            setControllerRequest(failedController, migrationRequest("/api/migrate/convert"));
            IOException failure = assertThrows(
                    IOException.class, failedController::convertToSqliteSqlFile);
            assertEquals("Legacy migration conversion failed", failure.getMessage());
            try (InstallOperationLock releasedAfterFailure = InstallOperationLock.tryAcquire(
                    InstallConstants.installConfig.getAction().getLockFile())) {
                assertNotNull(releasedAfterFailure);
            }

            Files.writeString(confPath.resolve("mysql.sql"),
                    "CREATE TABLE `log` (`id` int(11), `title` varchar(255));\n");
            ApiMigrateController retryController = new ApiMigrateController();
            CapturedResponse retryResponse = new CapturedResponse();
            setControllerRequest(retryController, migrationRequest("/api/migrate/convert"));
            setControllerResponse(retryController, retryResponse.response());
            retryController.convertToSqliteSqlFile();

            assertNotNull(retryResponse.json);
            assertTrue(Files.exists(confPath.resolve("sqlite.sql")));
            try (InstallOperationLock releasedAfterMigration = InstallOperationLock.tryAcquire(
                    InstallConstants.installConfig.getAction().getLockFile())) {
                assertNotNull(releasedAfterMigration);
            }
        } finally {
            InstallConstants.installConfig = previousConfig;
            restoreProperty("sws.conf.path", previousConfPath);
            delete(confPath);
        }
    }

    @Test
    public void shouldRejectMigrateConversionAfterInstalled() throws Exception {
        Path confPath = Files.createTempDirectory("zrlog-install-migrate-installed");
        String previousConfPath = System.getProperty("sws.conf.path");
        com.zrlog.install.web.config.InstallConfig previousConfig = InstallConstants.installConfig;
        try {
            System.setProperty("sws.conf.path", confPath.toString());
            InstallConstants.installConfig = installConfig(true);
            ApiMigrateController controller = new ApiMigrateController();
            CapturedResponse capturedResponse = new CapturedResponse();
            setControllerRequest(controller, migrationRequest("/api/migrate/convert"));
            setControllerResponse(controller, capturedResponse.response());

            controller.convertToSqliteSqlFile();

            assertEquals(Integer.valueOf(403), capturedResponse.code);
            assertFalse(Files.exists(confPath.resolve("sqlite.sql")));
        } finally {
            InstallConstants.installConfig = previousConfig;
            restoreProperty("sws.conf.path", previousConfPath);
            delete(confPath);
        }
    }

    @Test
    public void shouldRejectInsecureOrAmbiguousMigrateRequests() throws Exception {
        Path confPath = Files.createTempDirectory("zrlog-install-migrate-rejected");
        String previousConfPath = System.getProperty("sws.conf.path");
        com.zrlog.install.web.config.InstallConfig previousConfig = InstallConstants.installConfig;
        try {
            System.setProperty("sws.conf.path", confPath.toString());
            InstallConstants.installConfig = installConfig(false, false);

            ApiMigrateController getController = new ApiMigrateController();
            setControllerRequest(getController, request("/api/migrate/convert", Map.of()));
            assertThrows(InvalidInstallRequestException.class,
                    getController::convertToSqliteSqlFile);

            ApiMigrateController importGetController = new ApiMigrateController();
            setControllerRequest(importGetController, request("/api/migrate/import", Map.of()));
            assertThrows(InvalidInstallRequestException.class,
                    importGetController::doImportSqlite);

            ApiMigrateController formController = new ApiMigrateController();
            setControllerRequest(formController, request("/api/migrate/convert", Map.of(), Map.of(
                            "Content-Type", "application/x-www-form-urlencoded"),
                    false, HttpMethod.POST, "migration=legacy-mysql-to-sqlite"));
            assertThrows(InvalidInstallRequestException.class,
                    formController::convertToSqliteSqlFile);

            ApiMigrateController invalidContentTypeController = new ApiMigrateController();
            setControllerRequest(invalidContentTypeController, request("/api/migrate/convert", Map.of(), Map.of(
                            "Content-Type", "application/json-invalid"), false, HttpMethod.POST,
                    "{\"migration\":\"legacy-mysql-to-sqlite\"}"));
            assertThrows(InvalidInstallRequestException.class,
                    invalidContentTypeController::convertToSqliteSqlFile);

            ApiMigrateController emptyJsonController = new ApiMigrateController();
            setControllerRequest(emptyJsonController, jsonRequest("/api/migrate/convert", "{}"));
            assertThrows(InvalidInstallRequestException.class,
                    emptyJsonController::convertToSqliteSqlFile);

            ApiMigrateController queryOverrideController = new ApiMigrateController();
            setControllerRequest(queryOverrideController, request("/api/migrate/convert",
                    Map.of("sqlPath", "../outside.sql"), Map.of(
                            "Content-Type", "application/json"),
                    false, HttpMethod.POST,
                    "{\"migration\":\"legacy-mysql-to-sqlite\"}"));
            assertThrows(InvalidInstallRequestException.class,
                    queryOverrideController::convertToSqliteSqlFile);

            ApiMigrateController unknownMappingController = new ApiMigrateController();
            setControllerRequest(unknownMappingController, jsonRequest("/api/migrate/convert",
                    Map.of("migration", "../../outside.sql")));
            assertThrows(InvalidInstallRequestException.class,
                    unknownMappingController::convertToSqliteSqlFile);

            assertFalse(Files.exists(confPath.resolve("sqlite.sql")));
        } finally {
            InstallConstants.installConfig = previousConfig;
            restoreProperty("sws.conf.path", previousConfPath);
            delete(confPath);
        }
    }

    @Test
    public void shouldRewriteInstallPageAssetLinksWithContextPath() throws Exception {
        TestInstallController controller = new TestInstallController();
        Element link = Jsoup.parse("<a href=\"/favicon.ico\"></a>").selectFirst("a");

        controller.fill(request("/blog", new HashMap<>()), link);

        assertEquals("/blog/favicon.ico", link.attr("href"));
    }

    @Test
    public void shouldExposeNativeImageResourceRegistrationEntrypoint() {
        InstallNativeImageResourceUtils.main(new String[0]);
    }

    @Test
    public void shouldReportInstallInterceptorHandleableRoutes() {
        BlogInstallInterceptor interceptor = new BlogInstallInterceptor();

        assertTrue(interceptor.isHandleAble(request("/install", new HashMap<>())));
        assertTrue(interceptor.isHandleAble(request("/api/install/probe", new HashMap<>())));
        assertTrue(interceptor.isHandleAble(request("/install/static/js/main.js", new HashMap<>())));
        assertTrue(interceptor.isHandleAble(request("/install/static/css/main.css", new HashMap<>())));
        assertFalse(interceptor.isHandleAble(request("/install/static-malicious/main.js", new HashMap<>())));
        assertFalse(interceptor.isHandleAble(request("/blog", new HashMap<>())));
    }

    @Test
    public void shouldDispatchInstallInterceptorRequestsThroughInstallRoutes() throws Exception {
        BlogInstallInterceptor interceptor = new BlogInstallInterceptor();
        CapturedResponse pageResponse = new CapturedResponse();
        CapturedResponse probeResponse = new CapturedResponse();
        CapturedResponse resourceResponse = new CapturedResponse();

        assertFalse(interceptor.doInterceptor(
                routedRequest("/install", new HashMap<>()), pageResponse.response()));
        assertFalse(interceptor.doInterceptor(
                routedRequest("/api/install/probe", new HashMap<>()), probeResponse.response()));
        assertFalse(interceptor.doInterceptor(
                routedRequest("/api/install/installResource", new HashMap<>()), resourceResponse.response()));

        assertNotNull(pageResponse.html);
        assertEquals("no-store", pageResponse.headers.get("Cache-Control"));
        assertEquals("no-cache", pageResponse.headers.get("Pragma"));
        assertEquals("no-store", probeResponse.headers.get("Cache-Control"));
        assertEquals("no-cache", probeResponse.headers.get("Pragma"));
        assertEquals("no-store", resourceResponse.headers.get("Cache-Control"));
        assertEquals("no-cache", resourceResponse.headers.get("Pragma"));
        assertTrue(probeResponse.json instanceof InstallProbeResponse);
        assertTrue(resourceResponse.json instanceof InstallResourceResponse);
    }

    @Test
    public void shouldRejectInstallApiRequestsAfterInstalled() {
        BlogInstallInterceptor interceptor = new BlogInstallInterceptor();
        CapturedResponse capturedResponse = new CapturedResponse();
        com.zrlog.install.web.config.InstallConfig previous = InstallConstants.installConfig;
        try {
            InstallConstants.installConfig = installConfig(true);

            InstalledException exception = assertThrows(InstalledException.class, () ->
                    interceptor.doInterceptor(
                            routedRequest("/api/install/probe", new HashMap<>()), capturedResponse.response()));

            assertEquals(9020, exception.getError());
            assertEquals("no-store", capturedResponse.headers.get("Cache-Control"));
            assertEquals("no-cache", capturedResponse.headers.get("Pragma"));
        } finally {
            InstallConstants.installConfig = previous;
        }
    }

    private static void assertInvocationCause(Class<?> expectedCause, Map<String, String> params) throws Exception {
        TestApiInstallController controller = new TestApiInstallController();
        setControllerRequest(controller, jsonRequest("/api/install/testDbConn", params));

        try {
            controller.dbConn();
        } catch (RuntimeException e) {
            assertTrue(expectedCause.isInstance(e));
            return;
        }
        throw new AssertionError("Expected " + expectedCause.getName());
    }

    static void setControllerRequest(Controller controller, HttpRequest request) throws Exception {
        Field field = Controller.class.getDeclaredField("request");
        field.setAccessible(true);
        field.set(controller, request);
    }

    static void setControllerResponse(Controller controller, HttpResponse response) throws Exception {
        Field field = Controller.class.getDeclaredField("response");
        field.setAccessible(true);
        field.set(controller, response);
    }

    static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    static void delete(Path path) throws Exception {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private static Process startOperationLockHolder(java.io.File installLockFile,
                                                    Path readyFile, Path releaseFile) throws IOException {
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classPath = System.getProperty(
                "surefire.test.class.path", System.getProperty("java.class.path"));
        return new ProcessBuilder(javaExecutable, "-cp", classPath,
                InstallOperationLockProcess.class.getName(), installLockFile.getAbsolutePath(),
                readyFile.toString(), releaseFile.toString()).redirectErrorStream(true).start();
    }

    private static void waitForOperationLock(Process lockHolder, Path readyFile) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!Files.exists(readyFile) && lockHolder.isAlive() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue("External lock holder exited before acquiring the lock", lockHolder.isAlive());
        assertTrue("External lock holder did not acquire the lock in time", Files.exists(readyFile));
    }

    private static HttpRequest request(Map<String, String> params) {
        return request("/install", params);
    }

    private static HttpRequest request(String uri, Map<String, String> params) {
        return request(uri, params, Map.of());
    }

    static HttpRequest request(String uri, Map<String, String> params, Map<String, String> headers) {
        return request(uri, params, headers, false);
    }

    private static HttpRequest routedRequest(String uri, Map<String, String> params) {
        return request(uri, params, Map.of(), true);
    }

    private static HttpRequest routedJsonRequest(String uri, Map<String, String> headers) {
        Map<String, String> requestHeaders = new LinkedHashMap<>(headers);
        requestHeaders.put("Content-Type", "application/json;charset=UTF-8");
        return request(uri, Map.of(), requestHeaders, true, HttpMethod.POST, "{}");
    }

    static Map<String, String> installParams(String dbType) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("title", "ZrLog");
        params.put("second_title", "Install");
        params.put("username", "admin");
        params.put("password", "password");
        params.put("email", "admin@example.com");
        params.put("dbHost", "localhost");
        params.put("dbPort", "1");
        params.put("dbUserName", "root");
        params.put("dbPassword", "password");
        params.put("dbName", "zrlog");
        params.put("dbType", dbType);
        return params;
    }

    private static Map<String, String> sqliteInstallParams() {
        Map<String, String> params = installParams("sqlite");
        params.keySet().removeAll(java.util.Set.of(
                "dbHost", "dbPort", "dbUserName", "dbPassword", "dbName"));
        return params;
    }

    private static HttpRequest jsonRequest(String uri, Map<String, String> body) {
        return jsonRequest(uri, new Gson().toJson(body));
    }

    static HttpRequest migrationRequest(String uri) {
        return jsonRequest(uri, Map.of("migration", "legacy-mysql-to-sqlite"));
    }

    private static HttpRequest jsonRequest(String uri, String body) {
        return jsonRequest(uri, body, Map.of());
    }

    private static HttpRequest jsonRequest(String uri, Map<String, String> body, Map<String, String> headers) {
        return jsonRequest(uri, new Gson().toJson(body), headers);
    }

    private static HttpRequest jsonRequest(String uri, String body, Map<String, String> headers) {
        Map<String, String> requestHeaders = new LinkedHashMap<>(headers);
        requestHeaders.put("Content-Type", "application/json;charset=UTF-8");
        return request(uri, Map.of(), requestHeaders, false, HttpMethod.POST, body);
    }

    private static HttpRequest request(String uri, Map<String, String> params, Map<String, String> headers,
                                       boolean installRoutes) {
        return request(uri, params, headers, installRoutes, HttpMethod.GET, null);
    }

    private static HttpRequest request(String uri, Map<String, String> params, Map<String, String> headers,
                                       boolean installRoutes, HttpMethod httpMethod, String body) {
        Map<String, String[]> paramMap = new LinkedHashMap<>();
        params.forEach((key, value) -> paramMap.put(key, new String[]{value}));
        ServerConfig serverConfig = new ServerConfig();
        serverConfig.setContextPath("/blog");
        if (installRoutes) {
            InstallRouters.configRouter(serverConfig);
        }
        RequestConfig requestConfig = new RequestConfig();
        requestConfig.setRouter(serverConfig.getRouter());
        return (HttpRequest) Proxy.newProxyInstance(
                InstallWebLayerTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    if ("getUri".equals(method.getName())) {
                        return uri;
                    }
                    if ("getContextPath".equals(method.getName())) {
                        return "/blog";
                    }
                    if ("getQueryStr".equals(method.getName())) {
                        int queryStart = uri.indexOf('?');
                        return queryStart < 0 ? null : uri.substring(queryStart + 1);
                    }
                    if ("getServerConfig".equals(method.getName())) {
                        return serverConfig;
                    }
                    if ("getRequestConfig".equals(method.getName())) {
                        return requestConfig;
                    }
                    if ("getMethod".equals(method.getName())) {
                        return httpMethod;
                    }
                    if ("getParamMap".equals(method.getName()) || "decodeParamMap".equals(method.getName())) {
                        return paramMap;
                    }
                    if ("getParaToStr".equals(method.getName())) {
                        String key = args[0].toString();
                        String value = params.get(key);
                        if (args.length > 1) {
                            return value == null ? args[1].toString() : value;
                        }
                        return value;
                    }
                    if ("getHeader".equals(method.getName())) {
                        return headers.get(args[0].toString());
                    }
                    if ("getRequestBodyByteBuffer".equals(method.getName())) {
                        return ByteBuffer.wrap(body == null
                                ? new byte[0] : body.getBytes(StandardCharsets.UTF_8));
                    }
                    if ("toString".equals(method.getName())) {
                        return "HttpRequestProxy";
                    }
                    return null;
                });
    }

    private static DefaultInstallConfig installConfig(boolean installed) {
        return installConfig(installed, true);
    }

    static DefaultInstallConfig installConfig(boolean installed, boolean askConfig) {
        return new DefaultInstallConfig() {
            @Override
            public InstallAction getAction() {
                return new InstallAction() {
                    @Override
                    public void installSuccess() {
                    }

                    @Override
                    public boolean isInstalled() {
                        return installed;
                    }
                };
            }

            @Override
            public boolean isAskConfig() {
                return askConfig;
            }
        };
    }

    private static DefaultInstallConfig installConfigWithDbProperties(Path dbProperties, boolean warMode) {
        return new DefaultInstallConfig() {
            @Override
            public InstallAction getAction() {
                return new InstallAction() {
                    @Override
                    public void installSuccess() {
                    }

                    @Override
                    public java.io.File getLockFile() {
                        return dbProperties.resolveSibling("install.lock").toFile();
                    }
                };
            }

            @Override
            public java.io.File getDbPropertiesFile() {
                return dbProperties.toFile();
            }

            @Override
            public boolean isWarMode() {
                return warMode;
            }
        };
    }

    private static DefaultInstallConfig completionConfig(Path dbProperties, boolean installed, boolean askConfig) {
        return new DefaultInstallConfig() {
            @Override
            public InstallAction getAction() {
                return new InstallAction() {
                    @Override
                    public void installSuccess() {
                    }

                    @Override
                    public boolean isInstalled() {
                        return installed;
                    }

                    @Override
                    public java.io.File getLockFile() {
                        return dbProperties.resolveSibling("install.lock").toFile();
                    }
                };
            }

            @Override
            public java.io.File getDbPropertiesFile() {
                return dbProperties.toFile();
            }

            @Override
            public boolean isAskConfig() {
                return askConfig;
            }
        };
    }

    static class TestApiInstallController extends ApiInstallController {

        InstallDatabaseConfig dbConn() {
            return getDbConn();
        }

        boolean sseRequest() {
            return isSseRequest();
        }

        void installStream(InstallConfigVO configVO) throws Exception {
            writeInstallStream(configVO);
        }

        void upgradeStream() throws Exception {
            writeUpgradeStream();
        }

        InstallResultResponse installResult() {
            return buildInstallResultResponse();
        }
    }

    private static class TestApiMigrateController extends ApiMigrateController {

        static boolean batchDropTableSql(String sql) {
            return isBatchDropTableSql(sql);
        }

        static boolean dropSql(String sql) {
            return isDropSql(sql);
        }
    }

    private static class TestInstallController extends com.zrlog.install.web.controller.page.InstallController {

        void fill(HttpRequest request, Element link) {
            fillToRealLink(request, link);
        }
    }

    static class CapturedResponse {
        private final Map<String, String> headers = new HashMap<>();
        private final Map<String, String> addedHeaders = new HashMap<>();
        InputStream written;
        private String html;
        Object json;
        private Integer code;

        HttpResponse response() {
            return (HttpResponse) Proxy.newProxyInstance(
                    InstallWebLayerTest.class.getClassLoader(),
                    new Class[]{HttpResponse.class},
                    (proxy, method, args) -> {
                        if ("getHeader".equals(method.getName())) {
                            return headers;
                        }
                        if ("addHeader".equals(method.getName())) {
                            addedHeaders.put(args[0].toString(), args[1].toString());
                            return null;
                        }
                        if ("renderHtmlStr".equals(method.getName())) {
                            html = args[0].toString();
                            return null;
                        }
                        if ("renderJson".equals(method.getName())) {
                            json = args[0];
                            return null;
                        }
                        if ("renderCode".equals(method.getName())) {
                            code = (Integer) args[0];
                            return null;
                        }
                        if ("write".equals(method.getName())) {
                            written = (InputStream) args[0];
                            return null;
                        }
                        if ("toString".equals(method.getName())) {
                            return "HttpResponseProxy";
                        }
                        return null;
                    });
        }
    }
}
