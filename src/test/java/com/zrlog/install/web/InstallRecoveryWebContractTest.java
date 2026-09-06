package com.zrlog.install.web;

import com.google.gson.Gson;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.web.Controller;
import com.zrlog.install.business.response.InstallResourceResponse;
import com.zrlog.install.business.response.InstallResultResponse;
import com.zrlog.install.business.response.InstallRuntimeResourceResponse;
import com.zrlog.install.business.service.InstallOperationLock;
import com.zrlog.install.business.service.InstallService;
import com.zrlog.install.business.service.LocalSqliteSupport;
import com.zrlog.install.business.vo.InstallConfigVO;
import com.zrlog.install.business.vo.InstallDatabaseConfig;
import com.zrlog.install.business.vo.InstallSiteConfig;
import com.zrlog.install.exception.InstallRecoveryUnavailableException;
import com.zrlog.install.exception.InstallOperationInProgressException;
import com.zrlog.install.exception.InvalidInstallRequestException;
import com.zrlog.install.web.config.DefaultInstallConfig;
import com.zrlog.install.web.config.InstallConfig;
import com.zrlog.install.web.controller.api.ApiInstallController;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class InstallRecoveryWebContractTest {

    private static final String RECOVERY_FILE_NAME = ".install-recovery.properties";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private InstallConfig previousInstallConfig;

    @Before
    public void rememberInstallConfig() {
        previousInstallConfig = InstallConstants.installConfig;
    }

    @After
    public void restoreInstallConfig() {
        InstallConstants.installConfig = previousInstallConfig;
    }

    @Test
    public void shouldExposeAndResumeARecoverableSqliteInstallation() throws Exception {
        Path root = temporaryFolder.newFolder("recoverable-sqlite-install").toPath();
        RecoveryInstallConfig config = installConfig(root);
        config.failNextInstallSuccess();
        InstallConstants.installConfig = config;
        InstallDatabaseConfig databaseConfig = LocalSqliteSupport.createDatabaseConfig(config);

        assertFalse(new InstallService(config, installConfig(databaseConfig)).install());

        Path recoveryFile = recoveryFile(config);
        assertEquals(1, config.installSuccessCalls());
        assertTrue(Files.isRegularFile(recoveryFile));
        assertFalse(config.getDbPropertiesFile().exists());
        assertFalse(config.getAction().getLockFile().exists());

        ApiInstallController resourceController = controller(request(
                HttpMethod.GET, null, null));
        InstallResourceResponse resourceResponse = resourceController.installResource();
        assertTrue(resourceResponse.getData() instanceof InstallRuntimeResourceResponse);
        InstallRuntimeResourceResponse resource =
                (InstallRuntimeResourceResponse) resourceResponse.getData();
        assertEquals(Boolean.TRUE, resource.getInstallRecoveryAvailable());
        assertNull(resource.getInstallSuccessContent());
        String resourceJson = new Gson().toJson(resourceResponse);
        assertFalse(resourceJson.contains("jdbc:sqlite:"));
        assertFalse(resourceJson.contains("org.sqlite.JDBC"));
        assertFalse(resourceJson.contains(databaseConfig.getJdbcUrl().split("\\?", 2)[0]));

        assertInvalidResume(config, request(
                HttpMethod.GET, null, "{}"));
        assertInvalidResume(config, request(
                HttpMethod.POST, null, ""));

        InstallResultResponse result = controller(request(
                HttpMethod.POST, "ignored-when-disabled", "{}")).resumeInstall();

        assertNotNull(result.getData());
        assertEquals("", result.getData().getContent());
        assertEquals(2, config.installSuccessCalls());
        assertTrue(config.getDbPropertiesFile().isFile());
        assertTrue(config.getAction().getLockFile().isFile());
        assertFalse(Files.exists(recoveryFile));
        Properties storedDatabase = new Properties();
        try (InputStream inputStream = Files.newInputStream(config.getDbPropertiesFile().toPath())) {
            storedDatabase.load(inputStream);
        }
        assertEquals(databaseConfig.getJdbcUrl(), storedDatabase.getProperty("jdbcUrl"));
        assertEquals(databaseConfig.getDriverClass(), storedDatabase.getProperty("driverClass"));
    }

    @Test(timeout = 20000)
    public void shouldRejectCompletionDuringFailingCallbackAndReleaseOperationLock() throws Exception {
        Path root = temporaryFolder.newFolder("completion-callback-race").toPath();
        RecoveryInstallConfig config = installConfig(root);
        config.enableInstallCompletion();
        CountDownLatch callbackEntered = new CountDownLatch(1);
        CountDownLatch finishCallback = new CountDownLatch(1);
        config.failNextInstallSuccess(callbackEntered, finishCallback);
        InstallConstants.installConfig = config;
        InstallDatabaseConfig databaseConfig = LocalSqliteSupport.createDatabaseConfig(config);
        AtomicReference<Boolean> installResult = new AtomicReference<>();
        AtomicReference<Throwable> installFailure = new AtomicReference<>();
        Thread installThread = new Thread(() -> {
            try {
                installResult.set(new InstallService(
                        config, installConfig(databaseConfig)).install());
            } catch (Throwable e) {
                installFailure.set(e);
            }
        }, "install-completion-callback-race");

        try {
            installThread.start();
            assertTrue("Installation did not reach the host callback",
                    callbackEntered.await(10, TimeUnit.SECONDS));
            assertTrue(config.getDbPropertiesFile().isFile());
            assertTrue(config.getAction().getLockFile().isFile());

            InstallOperationInProgressException inProgress = assertThrows(
                    InstallOperationInProgressException.class,
                    () -> controller(request(HttpMethod.POST,
                            null, "{}")).installCompletion());
            assertEquals(9024, inProgress.getError());
            assertEquals("INSTALL_IN_PROGRESS", inProgress.getCode());
            assertFalse(inProgress.getMessage().contains(root.toAbsolutePath().toString()));
            assertFalse(inProgress.getMessage().contains(".install-operation.lock"));
        } finally {
            finishCallback.countDown();
            installThread.join(10000);
        }

        assertFalse("Installation thread is still running", installThread.isAlive());
        assertNull(installFailure.get());
        assertEquals(Boolean.FALSE, installResult.get());
        assertFalse(config.getDbPropertiesFile().exists());
        assertFalse(config.getAction().getLockFile().exists());
        assertTrue(Files.isRegularFile(recoveryFile(config)));

        InvalidInstallRequestException unavailable = assertThrows(
                InvalidInstallRequestException.class,
                () -> controller(request(HttpMethod.POST,
                        null, "{}")).installCompletion());
        assertEquals(9025, unavailable.getError());
        assertEquals("INVALID_INSTALL_REQUEST", unavailable.getCode());
        assertFalse(unavailable.getMessage().contains(root.toAbsolutePath().toString()));
        try (InstallOperationLock releasedLock = InstallOperationLock.tryAcquire(
                config.getAction().getLockFile())) {
            assertNotNull(releasedLock);
        }
    }

    @Test
    public void shouldReturnStableUnavailableErrorForMissingAndInvalidRecoveryState() throws Exception {
        RecoveryInstallConfig missingStateConfig = installConfig(
                temporaryFolder.newFolder("missing-recovery-state").toPath());
        assertRecoveryUnavailable(missingStateConfig);

        RecoveryInstallConfig invalidStateConfig = installConfig(
                temporaryFolder.newFolder("invalid-recovery-state").toPath());
        Path invalidRecoveryFile = recoveryFile(invalidStateConfig);
        Files.createDirectories(invalidRecoveryFile.getParent());
        Files.writeString(invalidRecoveryFile,
                "recoveryVersion=unsupported\n"
                        + "dbType=sqlite\n"
                        + "driverClass=org.sqlite.JDBC\n"
                        + "jdbcUrl=jdbc:sqlite:invalid.db\n",
                StandardCharsets.ISO_8859_1);

        assertRecoveryUnavailable(invalidStateConfig);
    }

    private static void assertInvalidResume(RecoveryInstallConfig config, HttpRequest request)
            throws Exception {
        InstallConstants.installConfig = config;
        InvalidInstallRequestException exception = assertThrows(
                InvalidInstallRequestException.class,
                () -> controller(request).resumeInstall());
        assertEquals(9025, exception.getError());
        assertEquals("INVALID_INSTALL_REQUEST", exception.getCode());
        assertTrue(Files.isRegularFile(recoveryFile(config)));
        assertFalse(config.getDbPropertiesFile().exists());
        assertFalse(config.getAction().getLockFile().exists());
    }

    private static void assertRecoveryUnavailable(RecoveryInstallConfig config) throws Exception {
        InstallConstants.installConfig = config;
        InstallRecoveryUnavailableException exception = assertThrows(
                InstallRecoveryUnavailableException.class,
                () -> controller(request(
                        HttpMethod.POST, null, "{}")).resumeInstall());
        assertEquals(9026, exception.getError());
        assertEquals("INSTALL_RECOVERY_UNAVAILABLE", exception.getCode());
    }

    private static InstallConfigVO installConfig(InstallDatabaseConfig databaseConfig) {
        InstallSiteConfig siteConfig = new InstallSiteConfig();
        siteConfig.setTitle("Recovery contract");
        siteConfig.setSecondTitle("SQLite installation");
        siteConfig.setUsername("admin");
        siteConfig.setPassword("strong-password");
        siteConfig.setEmail("admin@example.com");

        InstallConfigVO config = new InstallConfigVO();
        config.setDbConfig(databaseConfig);
        config.setConfigMsg(siteConfig);
        config.setContextPath("/blog");
        return config;
    }

    private static RecoveryInstallConfig installConfig(Path root) {
        Path configDirectory = root.resolve("conf");
        return new RecoveryInstallConfig(
                configDirectory.resolve("db.properties").toFile(),
                configDirectory.resolve("install.lock").toFile());
    }

    private static Path recoveryFile(RecoveryInstallConfig config) {
        return config.getAction().getLockFile().toPath().resolveSibling(RECOVERY_FILE_NAME);
    }

    private static ApiInstallController controller(HttpRequest request) throws Exception {
        ApiInstallController controller = new ApiInstallController();
        Field requestField = Controller.class.getDeclaredField("request");
        requestField.setAccessible(true);
        requestField.set(controller, request);
        return controller;
    }

    private static HttpRequest request(HttpMethod method, String token, String body) {
        ServerConfig serverConfig = new ServerConfig();
        serverConfig.setApplicationVersion("recovery-contract-test");
        serverConfig.setContextPath("/blog");
        Map<String, String> headers = token == null
                ? Map.of("Content-Type", "application/json;charset=UTF-8")
                : Map.of(
                        "Content-Type", "application/json;charset=UTF-8",
                        InstallRequestSecurity.TOKEN_HEADER, token);
        return (HttpRequest) Proxy.newProxyInstance(
                InstallRecoveryWebContractTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, reflectedMethod, arguments) -> {
                    switch (reflectedMethod.getName()) {
                        case "getMethod":
                            return method;
                        case "getHeader":
                            return headers.get(arguments[0].toString());
                        case "getRequestBodyByteBuffer":
                            return ByteBuffer.wrap(body == null
                                    ? new byte[0] : body.getBytes(StandardCharsets.UTF_8));
                        case "getContextPath":
                            return "/blog";
                        case "getServerConfig":
                            return serverConfig;
                        case "toString":
                            return "InstallRecoveryHttpRequest";
                        default:
                            return null;
                    }
                });
    }

    private static final class RecoveryInstallConfig extends DefaultInstallConfig {

        private final File dbPropertiesFile;
        private final File installLockFile;
        private final AtomicBoolean failNextInstallSuccess = new AtomicBoolean();
        private final AtomicInteger installSuccessCalls = new AtomicInteger();
        private volatile CountDownLatch callbackEntered;
        private volatile CountDownLatch finishCallback;
        private boolean askConfig;
        private final InstallAction installAction = new InstallAction() {
            @Override
            public void installSuccess() {
                installSuccessCalls.incrementAndGet();
                if (failNextInstallSuccess.compareAndSet(true, false)) {
                    CountDownLatch entered = callbackEntered;
                    CountDownLatch finish = finishCallback;
                    if (entered != null) {
                        entered.countDown();
                    }
                    if (finish != null) {
                        try {
                            if (!finish.await(10, TimeUnit.SECONDS)) {
                                throw new IllegalStateException(
                                        "Timed out waiting to fail the install callback");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("Install callback was interrupted", e);
                        }
                    }
                    throw new IllegalStateException("simulated host callback failure");
                }
            }

            @Override
            public File getLockFile() {
                return installLockFile;
            }

            @Override
            public boolean isInstalled() {
                return installLockFile.isFile();
            }
        };

        private RecoveryInstallConfig(File dbPropertiesFile, File installLockFile) {
            this.dbPropertiesFile = dbPropertiesFile;
            this.installLockFile = installLockFile;
        }

        void failNextInstallSuccess() {
            failNextInstallSuccess.set(true);
        }

        void failNextInstallSuccess(CountDownLatch entered, CountDownLatch finish) {
            callbackEntered = entered;
            finishCallback = finish;
            failNextInstallSuccess();
        }

        void enableInstallCompletion() {
            askConfig = true;
        }

        int installSuccessCalls() {
            return installSuccessCalls.get();
        }

        @Override
        public InstallAction getAction() {
            return installAction;
        }

        @Override
        public File getDbPropertiesFile() {
            return dbPropertiesFile;
        }

        @Override
        public boolean isAskConfig() {
            return askConfig;
        }

        @Override
        public boolean isMissingConfig() {
            return false;
        }

        @Override
        public String encryptPassword(String password) {
            return password;
        }
    }
}
