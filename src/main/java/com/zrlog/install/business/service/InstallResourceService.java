package com.zrlog.install.business.service;

import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.install.business.response.InstallRuntimeResourceResponse;
import com.zrlog.install.business.response.LastVersionInfo;
import com.zrlog.install.util.InstallSuccessContentUtils;
import com.zrlog.install.web.InstallConstants;

import java.nio.charset.Charset;
import java.util.Objects;

public class InstallResourceService {

    private String getFeedbackUrl(HttpRequest request) {
        return "https://blog.zrlog.com/feedback.html?v=" + Objects.requireNonNullElse(request.getServerConfig().getApplicationVersion(), "3")
                + "&os=" + System.getProperty("os.name");
    }

    public Object installResourceInfo(HttpRequest request) {
        InstallRuntimeResourceResponse response = new InstallRuntimeResourceResponse();
        response.setLang(InstallConstants.installConfig.getAcceptLanguage());
        response.setInstalled(InstallConstants.installConfig.getAction().isInstalled());
        response.setAskConfig(InstallConstants.installConfig.isAskConfig());
        response.setMissingConfig(InstallConstants.installConfig.isMissingConfig());
        response.setWarMode(InstallConstants.installConfig.isWarMode());
        response.setCurrentVersion(InstallConstants.installConfig.getBuildVersion());
        response.setCharset(Charset.defaultCharset().displayName());
        response.setRuntimeMode(new InstallProbeService().probe(InstallConstants.installConfig).getRuntimeMode());
        response.setDbPropertiesPath(InstallConstants.installConfig.getDbPropertiesFile().getAbsolutePath());
        response.setLockFilePath(InstallConstants.installConfig.getAction().getLockFile().getAbsolutePath());
        response.setOnlineUpgradable(InstallConstants.installConfig.getUpgradeAction().isSupported());
        response.setFeedbackUrl(getFeedbackUrl(request));
        if (Objects.equals(response.getInstalled(), true) && InstallConstants.installConfig.isAskConfig()) {
            response.setInstallSuccessContent(InstallSuccessContentUtils.getContent(InstallConstants.installConfig.getDbPropertiesFile(), true, request.getServerConfig()));
        }
        LastVersionInfo lastVersionInfo = InstallConstants.installConfig.getLastVersionInfo();
        if (Objects.nonNull(lastVersionInfo) && Objects.equals(lastVersionInfo.getLatestVersion(), false)) {
            response.setUpgradeVersion(lastVersionInfo.getNewVersion());
            response.setUpgradeChangeLog(lastVersionInfo.getChangeLog());
            response.setUpgradeDownloadUrl(lastVersionInfo.getDownloadUrl());
        }
        return response;
    }
}
