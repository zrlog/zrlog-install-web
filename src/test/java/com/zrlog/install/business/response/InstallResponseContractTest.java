package com.zrlog.install.business.response;

import com.zrlog.install.business.type.TestConnectDbResult;
import com.zrlog.install.business.vo.InstallSuccessData;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class InstallResponseContractTest {

    @Test
    public void shouldExposeStandardResponseFields() {
        TestConnectResponse response = new TestConnectResponse();

        response.setError(12);
        response.setMessage("failed");

        assertEquals(12, response.getError());
        assertEquals("failed", response.getMessage());
    }

    @Test
    public void shouldExposeProbeResponseData() {
        InstallProbeItem item = new InstallProbeItem("db", "database", "ok", "mysql");
        InstallProbeData data = new InstallProbeData();
        data.setStatus("ready");
        data.setRuntimeMode("jar");
        data.setCharset("UTF-8");
        data.setItems(Collections.singletonList(item));

        InstallProbeResponse response = new InstallProbeResponse(data);

        assertSame(data, response.getData());
        assertEquals("ready", response.getData().getStatus());
        assertEquals("jar", response.getData().getRuntimeMode());
        assertEquals("UTF-8", response.getData().getCharset());
        assertEquals("db", response.getData().getItems().get(0).getCode());
        assertEquals("database", response.getData().getItems().get(0).getCategory());
        assertEquals("ok", response.getData().getItems().get(0).getStatus());
        assertEquals("mysql", response.getData().getItems().get(0).getValue());
    }

    @Test
    public void shouldExposeProbeItemSetters() {
        InstallProbeItem item = new InstallProbeItem();

        item.setCode("lock");
        item.setCategory("file");
        item.setStatus("warning");
        item.setValue("missing");

        assertEquals("lock", item.getCode());
        assertEquals("file", item.getCategory());
        assertEquals("warning", item.getStatus());
        assertEquals("missing", item.getValue());
    }

    @Test
    public void shouldExposeResourceAndResultPayloads() {
        Object resourceData = Arrays.asList("install", "resource");
        InstallResourceResponse resourceResponse = new InstallResourceResponse(resourceData);
        InstallSuccessData successData = new InstallSuccessData("done");
        InstallResultResponse resultResponse = new InstallResultResponse(successData);

        assertSame(resourceData, resourceResponse.getData());
        assertEquals("done", resultResponse.getData().getContent());

        resourceResponse.setData("changed");
        resultResponse.setData(new InstallSuccessData("changed-content"));

        assertEquals("changed", resourceResponse.getData());
        assertEquals("changed-content", resultResponse.getData().getContent());
    }

    @Test
    public void shouldExposeDefaultResponseConstructors() {
        InstallProbeData probeData = new InstallProbeData();
        InstallProbeResponse probeResponse = new InstallProbeResponse();
        InstallResourceResponse resourceResponse = new InstallResourceResponse();
        InstallSuccessData successData = new InstallSuccessData();
        InstallResultResponse resultResponse = new InstallResultResponse();

        probeResponse.setData(probeData);
        resourceResponse.setData(probeData);
        successData.setContent("success");
        resultResponse.setData(successData);

        assertSame(probeData, probeResponse.getData());
        assertSame(probeData, resourceResponse.getData());
        assertEquals("success", resultResponse.getData().getContent());
    }

    @Test
    public void shouldExposeInstalledResourceFields() {
        InstalledResResponse response = new InstalledResResponse();

        response.setInstalled(true);
        response.setInstalledTitle("installed");
        response.setInstalledTips("tips");
        response.setMissingConfigTips("missing");
        response.setLang("zh_CN");
        response.setAskConfig(false);
        response.setAskConfigTips("ask");
        response.setInstallSuccessContent("content");

        assertEquals(true, response.getInstalled());
        assertEquals("installed", response.getInstalledTitle());
        assertEquals("tips", response.getInstalledTips());
        assertEquals("missing", response.getMissingConfigTips());
        assertEquals("zh_CN", response.getLang());
        assertEquals(false, response.getAskConfig());
        assertEquals("ask", response.getAskConfigTips());
        assertEquals("content", response.getInstallSuccessContent());
    }

    @Test
    public void shouldExposeRuntimeResourceFields() {
        InstallRuntimeResourceResponse response = new InstallRuntimeResourceResponse();

        response.setInstalled(true);
        response.setAskConfig(false);
        response.setMissingConfig(false);
        response.setWarMode(true);
        response.setLang("en_US");
        response.setCurrentVersion("3.6.0");
        response.setInstallSuccessContent("content");
        response.setUpgradeVersion("3.7.0");
        response.setUpgradeChangeLog("changes");
        response.setUpgradeDownloadUrl("https://example.com/zrlog.zip");
        response.setFeedbackUrl("https://example.com/feedback");
        response.setCharset("UTF-8");
        response.setRuntimeMode("war");
        response.setLocalSqliteAvailable(false);
        response.setInstallRecoveryAvailable(true);
        response.setInstallTokenRequired(true);

        assertEquals(true, response.getInstalled());
        assertEquals(false, response.getAskConfig());
        assertEquals(false, response.getMissingConfig());
        assertEquals(true, response.getWarMode());
        assertEquals("en_US", response.getLang());
        assertEquals("3.6.0", response.getCurrentVersion());
        assertEquals("content", response.getInstallSuccessContent());
        assertEquals("3.7.0", response.getUpgradeVersion());
        assertEquals("changes", response.getUpgradeChangeLog());
        assertEquals("https://example.com/zrlog.zip", response.getUpgradeDownloadUrl());
        assertEquals("https://example.com/feedback", response.getFeedbackUrl());
        assertEquals("UTF-8", response.getCharset());
        assertEquals("war", response.getRuntimeMode());
        assertEquals(false, response.getLocalSqliteAvailable());
        assertEquals(true, response.getInstallRecoveryAvailable());
        assertEquals(true, response.getInstallTokenRequired());
    }

    @Test
    public void shouldExposeLastVersionInfoFields() {
        LastVersionInfo info = new LastVersionInfo();

        info.setNewVersion("3.7.0");
        info.setDownloadUrl("https://example.com/zrlog.zip");
        info.setLatestVersion(false);
        info.setChangeLog("changes");

        assertEquals("3.7.0", info.getNewVersion());
        assertEquals("https://example.com/zrlog.zip", info.getDownloadUrl());
        assertEquals(false, info.getLatestVersion());
        assertEquals("changes", info.getChangeLog());
    }

    @Test
    public void shouldKeepStableDbResultErrorCodes() {
        assertEquals(0, TestConnectDbResult.SUCCESS.getError());
        assertEquals(1, TestConnectDbResult.DB_NOT_EXISTS.getError());
        assertEquals(2, TestConnectDbResult.CREATE_CONNECT_ERROR.getError());
        assertEquals(3, TestConnectDbResult.USERNAME_OR_PASSWORD_ERROR.getError());
        assertEquals(4, TestConnectDbResult.SQL_EXCEPTION_UNKNOWN.getError());
        assertEquals(5, TestConnectDbResult.MISSING_JDBC_DRIVER.getError());
        assertEquals(6, TestConnectDbResult.UNKNOWN.getError());
    }
}
