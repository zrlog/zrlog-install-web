package com.zrlog.install.business.service;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.config.ServerConfig;
import com.zrlog.install.business.response.InstallRuntimeResourceResponse;
import com.zrlog.install.business.response.LastVersionInfo;
import com.zrlog.install.web.InstallConstants;
import com.zrlog.install.web.config.DefaultInstallConfig;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
        assertEquals(config.getDbPropertiesFile().getAbsolutePath(), response.getDbPropertiesPath());
        assertEquals(config.getAction().getLockFile().getAbsolutePath(), response.getLockFilePath());
        assertEquals("3.7.0", response.getUpgradeVersion());
        assertEquals("changes", response.getUpgradeChangeLog());
        assertEquals("https://example.com/zrlog.zip", response.getUpgradeDownloadUrl());
        assertEquals(false, response.getOnlineUpgradable());
        assertNotNull(response.getCharset());
        assertTrue(response.getFeedbackUrl().contains("v=3.6.1-SNAPSHOT"));
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
