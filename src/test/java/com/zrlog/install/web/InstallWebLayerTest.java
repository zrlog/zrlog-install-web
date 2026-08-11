package com.zrlog.install.web;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.web.Controller;
import com.hibegin.http.server.web.MethodInterceptor;
import com.zrlog.install.business.type.TestConnectDbResult;
import com.zrlog.install.business.response.InstallProbeResponse;
import com.zrlog.install.business.response.InstallResourceResponse;
import com.zrlog.install.business.response.InstallResultResponse;
import com.zrlog.install.business.response.InstallRuntimeResourceResponse;
import com.zrlog.install.business.response.InstallUpgradeResult;
import com.zrlog.install.business.service.InstallUpgradeAction;
import com.zrlog.install.business.vo.InstallConfigVO;
import com.zrlog.install.business.vo.InstallDatabaseConfig;
import com.zrlog.install.exception.MissingDbHostException;
import com.zrlog.install.exception.MissingDbNameException;
import com.zrlog.install.exception.MissingDbPortException;
import com.zrlog.install.exception.MissingDbUserNameException;
import com.zrlog.install.exception.InstallException;
import com.zrlog.install.exception.InstalledException;
import com.zrlog.install.util.InstallNativeImageResourceUtils;
import com.zrlog.install.util.InstallSseEmitter;
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

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
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

        InstallSseEmitter.write(capturedResponse.response(), "install-test", "install-error",
                emitter -> {
                    throw new IllegalStateException("boom");
                });

        String body = new String(capturedResponse.written.readAllBytes());
        assertTrue(body.contains("event: install-error"));
        assertTrue(body.contains("\"message\":\"boom\""));
    }

    @Test
    public void shouldBuildDatabaseConnectionMapFromControllerRequest() throws Exception {
        TestApiInstallController controller = new TestApiInstallController();
        setControllerRequest(controller, request(Map.of(
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
    public void shouldBuildServerOwnedSqliteConnectionWithoutNetworkParams() throws Exception {
        Path root = Files.createTempDirectory("zrlog-install-sqlite-config");
        com.zrlog.install.web.config.InstallConfig previousConfig = InstallConstants.installConfig;
        try {
            InstallConstants.installConfig = installConfigWithDbProperties(root.resolve("conf/db.properties"), false);
            TestApiInstallController controller = new TestApiInstallController();
            setControllerRequest(controller, request(Map.of("dbType", "sqlite")));

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
            setControllerRequest(controller, request(Map.of("dbType", "sqlite")));

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
            setControllerRequest(controller, request("/api/install/start", params));
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
    public void shouldRenderInstallIndexWithRuntimeResourceInfo() throws Exception {
        com.zrlog.install.web.controller.page.InstallController controller =
                new com.zrlog.install.web.controller.page.InstallController();
        CapturedResponse capturedResponse = new CapturedResponse();
        setControllerRequest(controller, request("/install", new HashMap<>()));
        setControllerResponse(controller, capturedResponse.response());

        controller.index();

        assertNotNull(capturedResponse.html);
        Document document = Jsoup.parse(capturedResponse.html);
        assertEquals("ZrLog - Install", document.title());
        assertEquals("/blog/", document.selectFirst("base").attr("href"));
        assertEquals(InstallConstants.installConfig.getAcceptLanguage().split("_")[0],
                document.selectFirst("html").attr("lang"));
        assertFalse(document.body().hasClass("dark"));
        assertFalse(document.body().hasClass("light"));
        assertTrue(document.getElementById("resourceInfo").text().contains("\"feedbackUrl\""));
        assertTrue(document.getElementById("resourceInfo").text().contains("blog.zrlog.com"));
        assertTrue(document.getElementById("resourceInfo").text().contains("feedback.html"));
        assertTrue(document.head().select("link[rel=shortcut icon]").attr("href").startsWith("/blog/"));
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
        setControllerRequest(testController, request("/api/install/testDbConn", new HashMap<>()));

        assertThrows(MissingDbHostException.class, testController::testDbConn);

        ApiInstallController installController = new ApiInstallController();
        setControllerRequest(installController, request("/api/install/start", new HashMap<>()));
        setControllerResponse(installController, new CapturedResponse().response());

        assertThrows(MissingDbHostException.class, installController::startInstall);
    }

    @Test
    public void shouldThrowInstallExceptionWhenDatabaseConnectionTestFails() throws Exception {
        ApiInstallController controller = new ApiInstallController();
        setControllerRequest(controller, request("/api/install/testDbConn", installParams("unsupported")));

        InstallException exception = assertThrows(InstallException.class, controller::testDbConn);

        assertEquals(9000, exception.getError());
        assertEquals(TestConnectDbResult.MISSING_JDBC_DRIVER.name(), exception.getCode());
    }

    @Test
    public void shouldThrowInstallExceptionWhenNormalInstallFails() throws Exception {
        Path confPath = Files.createTempDirectory("zrlog-install-controller-fail");
        String previousConfPath = System.getProperty("sws.conf.path");
        try {
            System.setProperty("sws.conf.path", confPath.toString());
            ApiInstallController controller = new ApiInstallController();
            setControllerRequest(controller, request("/api/install/start", installParams("unsupported")));
            setControllerResponse(controller, new CapturedResponse().response());

            InstallException exception = assertThrows(InstallException.class, controller::startInstall);

            assertEquals(9000, exception.getError());
            assertEquals(TestConnectDbResult.UNKNOWN.name(), exception.getCode());
        } finally {
            restoreProperty("sws.conf.path", previousConfPath);
            delete(confPath);
        }
    }

    @Test
    public void shouldWriteInstallErrorEventWhenSseInstallFails() throws Exception {
        Path confPath = Files.createTempDirectory("zrlog-install-controller-sse-fail");
        String previousConfPath = System.getProperty("sws.conf.path");
        try {
            System.setProperty("sws.conf.path", confPath.toString());
            ApiInstallController controller = new ApiInstallController();
            CapturedResponse capturedResponse = new CapturedResponse();
            setControllerRequest(controller, request("/api/install/start", installParams("unsupported"),
                    Map.of("Accept", "text/event-stream")));
            setControllerResponse(controller, capturedResponse.response());

            controller.startInstall();

            String body = new String(capturedResponse.written.readAllBytes());
            assertTrue(body.contains("event: install-progress"));
            assertTrue(body.contains("event: install-error"));
            assertTrue(body.contains("\"code\":\"database\""));
        } finally {
            restoreProperty("sws.conf.path", previousConfPath);
            delete(confPath);
        }
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
    public void shouldConvertMysqlSqlToSqliteFile() throws Exception {
        Path confPath = Files.createTempDirectory("zrlog-install-migrate");
        String previousConfPath = System.getProperty("sws.conf.path");
        try {
            System.setProperty("sws.conf.path", confPath.toString());
            Files.writeString(confPath.resolve("mysql.sql"), ""
                    + "DROP TABLE IF EXISTS `a`, `b`;\n"
                    + "CREATE TABLE `log` (`id` int(11) NOT NULL AUTO_INCREMENT, `title` varchar(255), PRIMARY KEY (`id`)) ENGINE=InnoDB DEFAULT CHARSET=utf8;\n"
                    + "INSERT INTO `log` VALUES (1,'hello'),(2,'world');\n");
            ApiMigrateController controller = new ApiMigrateController();
            CapturedResponse capturedResponse = new CapturedResponse();
            setControllerRequest(controller, request("/api/migrate/convert", Map.of("sqlPath", "mysql.sql")));
            setControllerResponse(controller, capturedResponse.response());

            controller.convertToSqliteSqlFile();

            String sqliteSql = Files.readString(confPath.resolve("sqlite.sql"));
            assertNotNull(capturedResponse.json);
            assertFalse(sqliteSql.contains("DROP TABLE IF EXISTS `a`, `b`"));
            assertTrue(sqliteSql.contains("CREATE TABLE `log`"));
            assertTrue(sqliteSql.contains("INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT"));
            assertTrue(sqliteSql.contains("INSERT INTO `log` VALUES (1,'hello')"));
            assertTrue(sqliteSql.contains("INSERT INTO `log` VALUES (2,'world')"));
        } finally {
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
            setControllerRequest(controller, request("/api/migrate/convert", Map.of("sqlPath", "mysql.sql")));
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
        assertFalse(interceptor.isHandleAble(request("/blog", new HashMap<>())));
    }

    @Test
    public void shouldDispatchInstallInterceptorRequestsThroughInstallRoutes() throws Exception {
        BlogInstallInterceptor interceptor = new BlogInstallInterceptor();
        CapturedResponse probeResponse = new CapturedResponse();
        CapturedResponse resourceResponse = new CapturedResponse();

        assertFalse(interceptor.doInterceptor(
                routedRequest("/api/install/probe", new HashMap<>()), probeResponse.response()));
        assertFalse(interceptor.doInterceptor(
                routedRequest("/api/install/installResource", new HashMap<>()), resourceResponse.response()));

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
        } finally {
            InstallConstants.installConfig = previous;
        }
    }

    private static void assertInvocationCause(Class<?> expectedCause, Map<String, String> params) throws Exception {
        TestApiInstallController controller = new TestApiInstallController();
        setControllerRequest(controller, request(params));

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

    private static HttpRequest request(String uri, Map<String, String> params, Map<String, String> headers,
                                       boolean installRoutes) {
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
                    if ("getServerConfig".equals(method.getName())) {
                        return serverConfig;
                    }
                    if ("getRequestConfig".equals(method.getName())) {
                        return requestConfig;
                    }
                    if ("getMethod".equals(method.getName())) {
                        return HttpMethod.GET;
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
