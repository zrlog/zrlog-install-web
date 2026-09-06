package com.zrlog.install.business.service;

import com.google.gson.Gson;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.config.ServerConfig;
import com.zrlog.install.business.response.InstallRuntimeResourceResponse;
import com.zrlog.install.business.response.LastVersionInfo;
import com.zrlog.install.business.vo.InstallDatabaseConfig;
import com.zrlog.install.business.vo.InstallConfigVO;
import com.zrlog.install.web.InstallAction;
import com.zrlog.install.web.InstallConstants;
import com.zrlog.install.web.config.DefaultInstallConfig;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class InstallResourceServiceTest {

    @After
    public void tearDown() {
        InstallConstants.installConfig = new DefaultInstallConfig();
    }

    @Test
    public void shouldBuildRuntimeResourceInfoFromInstallConfig() throws Exception {
        File root = Files.createTempDirectory("zrlog-install-resource").toFile();
        FakeInstallConfig config = new FakeInstallConfig(
                new File(root, "db.properties"),
                new File(root, "install.lock"));
        config.setAcceptLanguage("en_US");
        config.setBuildVersion("3.6.1-SNAPSHOT");
        config.setAskConfig(true);
        config.setMissingConfig(true);
        config.setWarMode(true);
        config.setLastVersionInfo(upgradableVersion());
        InstallConstants.installConfig = config;

        InstallRuntimeResourceResponse response = (InstallRuntimeResourceResponse)
                new InstallResourceService().installResourceInfo(request("3.6.1-SNAPSHOT"));

        assertEquals("en_US", response.getLang());
        assertEquals(false, response.getInstalled());
        assertEquals(true, response.getAskConfig());
        assertEquals(true, response.getMissingConfig());
        assertEquals(true, response.getWarMode());
        assertEquals("3.6.1-SNAPSHOT", response.getCurrentVersion());
        assertEquals("war", response.getRuntimeMode());
        assertEquals(false, response.getLocalSqliteAvailable());
        assertEquals(false, response.getInstallRecoveryAvailable());
        assertEquals(false, response.getInstallOperationInProgress());
        assertEquals(false, response.getInstallTokenRequired());
        assertEquals("3.7.0", response.getUpgradeVersion());
        assertEquals("changes", response.getUpgradeChangeLog());
        assertEquals("https://example.com/zrlog.zip", response.getUpgradeDownloadUrl());
        assertEquals(false, response.getOnlineUpgradable());
        assertNotNull(response.getCharset());
        assertTrue(response.getFeedbackUrl().contains("v=3.6.1-SNAPSHOT"));
        String json = new Gson().toJson(response);
        assertFalse(json.contains(root.getAbsolutePath()));
        assertFalse(json.contains("dbPropertiesPath"));
        assertFalse(json.contains("lockFilePath"));
    }

    @Test
    public void shouldExposeOnlyWhetherInstallTokenIsRequired() throws Exception {
        File root = Files.createTempDirectory("zrlog-install-token-resource").toFile();
        InstallConstants.installConfig = new FakeInstallConfig(
                new File(root, "db.properties"), new File(root, "install.lock"));

        InstallRuntimeResourceResponse response = (InstallRuntimeResourceResponse)
                new InstallResourceService(() -> true).installResourceInfo(request("3.6.1"));
        String json = new Gson().toJson(response);

        assertEquals(true, response.getInstallTokenRequired());
        assertTrue(json.contains("\"installTokenRequired\":true"));
    }

    @Test
    public void shouldHideSetupSecretsAndPathsAfterInstallation() throws Exception {
        File root = Files.createTempDirectory("zrlog-installed-resource").toFile();
        File dbProperties = new File(root, "db.properties");
        Files.write(dbProperties.toPath(), Arrays.asList(
                "jdbcUrl=jdbc:mysql://localhost:3306/zrlog",
                "user=zrlog",
                "password=plain-text-secret"), StandardCharsets.UTF_8);
        FakeInstallConfig config = new FakeInstallConfig(dbProperties, new File(root, "install.lock"));
        config.setInstalled(true);
        config.setAskConfig(true);
        new InstallRecoveryStore().save(config.getAction().getLockFile(), InstallDatabaseConfig.from(Map.of(
                "dbType", "sqlite",
                "jdbcUrl", "jdbc:sqlite:" + new File(root, "zrlog.db").getAbsolutePath(),
                "driverClass", "org.sqlite.JDBC")));
        InstallConstants.installConfig = config;

        InstallRuntimeResourceResponse response = (InstallRuntimeResourceResponse)
                new InstallResourceService().installResourceInfo(request("3.6.1"));

        assertEquals(true, response.getInstalled());
        assertNull(response.getInstallSuccessContent());
        assertEquals(false, response.getInstallRecoveryAvailable());
        assertEquals(false, response.getInstallOperationInProgress());
        assertFalse(new InstallRecoveryStore().isAvailable(config.getAction().getLockFile()));
    }

    @Test
    public void shouldExposeOnlyThePresenceOfRecoverableInstallState() throws Exception {
        File root = Files.createTempDirectory("zrlog-recoverable-resource").toFile();
        FakeInstallConfig config = new FakeInstallConfig(
                new File(root, "db.properties"), new File(root, "install.lock"));
        new InstallRecoveryStore().save(config.getAction().getLockFile(), InstallDatabaseConfig.from(Map.of(
                "dbType", "sqlite",
                "jdbcUrl", "jdbc:sqlite:" + new File(root, "zrlog.db").getAbsolutePath(),
                "driverClass", "org.sqlite.JDBC")));
        InstallConstants.installConfig = config;

        InstallRuntimeResourceResponse response = (InstallRuntimeResourceResponse)
                new InstallResourceService().installResourceInfo(request("3.6.1"));

        assertEquals(true, response.getInstallRecoveryAvailable());
        assertEquals(false, response.getInstallOperationInProgress());
        assertNull(response.getInstallSuccessContent());
    }

    @Test
    public void shouldNotExposeMalformedRecoveryState() throws Exception {
        File root = Files.createTempDirectory("zrlog-malformed-recovery-resource").toFile();
        FakeInstallConfig config = new FakeInstallConfig(
                new File(root, "db.properties"), new File(root, "install.lock"));
        Files.writeString(InstallRecoveryStore.recoveryFile(
                config.getAction().getLockFile()).toPath(), "recoveryVersion=unsupported\n");
        InstallConstants.installConfig = config;

        InstallRuntimeResourceResponse response = (InstallRuntimeResourceResponse)
                new InstallResourceService().installResourceInfo(request("3.6.1"));

        assertEquals(false, response.getInstallRecoveryAvailable());
    }

    @Test
    public void shouldExposeProbeStateWhenTheOperationLockCannotBeCreated() throws Exception {
        Path root = Files.createTempDirectory("zrlog-install-resource-invalid-lock-parent");
        Path invalidParent = root.resolve("not-a-directory");
        Files.writeString(invalidParent, "regular file", StandardCharsets.UTF_8);
        FakeInstallConfig config = new FakeInstallConfig(
                root.resolve("db.properties").toFile(),
                invalidParent.resolve("install.lock").toFile());
        InstallConstants.installConfig = config;

        InstallRuntimeResourceResponse response = (InstallRuntimeResourceResponse)
                new InstallResourceService().installResourceInfo(request("3.6.1"));

        assertEquals(false, response.getInstalled());
        assertEquals(false, response.getInstallRecoveryAvailable());
        assertEquals(false, response.getInstallOperationInProgress());
        assertFalse(new Gson().toJson(response).contains(root.toString()));
    }

    @Test
    public void shouldPreserveRecoveryStateWhenInstallResourceIsReadDuringFailingCallback() throws Exception {
        File root = Files.createTempDirectory("zrlog-install-resource-callback-race").toFile();
        File dbProperties = new File(root, "conf/db.properties");
        File installLock = new File(root, "conf/install.lock");
        CountDownLatch callbackEntered = new CountDownLatch(1);
        CountDownLatch finishCallback = new CountDownLatch(1);
        AtomicInteger callbackCalls = new AtomicInteger();
        AtomicReference<Boolean> firstInstallResult = new AtomicReference<>();
        AtomicReference<Throwable> installThreadFailure = new AtomicReference<>();
        InstallAction action = new InstallAction() {
            @Override
            public void installSuccess() {
                if (callbackCalls.incrementAndGet() != 1) {
                    return;
                }
                callbackEntered.countDown();
                try {
                    if (!finishCallback.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to fail the install callback");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Install callback was interrupted", e);
                }
                throw new IllegalStateException("simulated callback failure");
            }

            @Override
            public File getLockFile() {
                return installLock;
            }
        };
        FakeInstallConfig config = new FakeInstallConfig(dbProperties, installLock) {
            @Override
            public InstallAction getAction() {
                return action;
            }
        };
        InstallConstants.installConfig = config;
        InstallConfigVO installRequest = new InstallConfigVO();
        installRequest.setDbConfig(Map.of("dbType", "sqlite"));
        installRequest.setConfigMsg(Map.of(
                "title", "Recovery Blog",
                "second_title", "Callback recovery",
                "username", "admin",
                "password", "password",
                "email", "admin@example.com"));
        Thread installThread = new Thread(() -> {
            try {
                firstInstallResult.set(new InstallService(config, installRequest).install());
            } catch (Throwable e) {
                installThreadFailure.set(e);
            }
        }, "install-resource-callback-race");

        try {
            installThread.start();
            assertTrue("Installation did not reach the host callback",
                    callbackEntered.await(10, TimeUnit.SECONDS));
            assertTrue(installLock.exists());
            assertTrue(new InstallRecoveryStore().isAvailable(installLock));

            InstallRuntimeResourceResponse installingResponse = (InstallRuntimeResourceResponse)
                    new InstallResourceService().installResourceInfo(request("3.6.1"));

            assertEquals(false, installingResponse.getInstalled());
            assertEquals(false, installingResponse.getInstallRecoveryAvailable());
            assertEquals(true, installingResponse.getInstallOperationInProgress());
            assertTrue(new InstallRecoveryStore().isAvailable(installLock));
        } finally {
            finishCallback.countDown();
            installThread.join(10000);
        }

        assertFalse("Installation thread is still running", installThread.isAlive());
        assertNull(installThreadFailure.get());
        assertEquals(Boolean.FALSE, firstInstallResult.get());
        assertFalse(installLock.exists());
        assertTrue(new InstallRecoveryStore().isAvailable(installLock));

        InstallRuntimeResourceResponse recoveryResponse = (InstallRuntimeResourceResponse)
                new InstallResourceService().installResourceInfo(request("3.6.1"));
        assertEquals(false, recoveryResponse.getInstalled());
        assertEquals(true, recoveryResponse.getInstallRecoveryAvailable());
        assertEquals(false, recoveryResponse.getInstallOperationInProgress());
        assertTrue(new InstallService(config, new InstallConfigVO()).resume());
        assertEquals(2, callbackCalls.get());
        assertTrue(installLock.exists());
        assertFalse(new InstallRecoveryStore().isAvailable(installLock));
    }

    private static LastVersionInfo upgradableVersion() {
        LastVersionInfo info = new LastVersionInfo();
        info.setLatestVersion(false);
        info.setNewVersion("3.7.0");
        info.setChangeLog("changes");
        info.setDownloadUrl("https://example.com/zrlog.zip");
        return info;
    }

    private static HttpRequest request(String applicationVersion) {
        ServerConfig serverConfig = new ServerConfig().setApplicationVersion(applicationVersion);
        return (HttpRequest) Proxy.newProxyInstance(
                InstallResourceServiceTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    if ("getServerConfig".equals(method.getName())) {
                        return serverConfig;
                    }
                    if ("toString".equals(method.getName())) {
                        return "HttpRequestProxy";
                    }
                    return null;
                });
    }

}
