package com.zrlog.install.business.service;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.install.business.response.InstallRuntimeResourceResponse;
import com.zrlog.install.business.response.LastVersionInfo;
import com.zrlog.install.util.InstallLogUtil;
import com.zrlog.install.web.InstallAction;
import com.zrlog.install.web.InstallConstants;
import com.zrlog.install.web.InstallRequestSecurity;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class InstallResourceService {

    private static final Logger LOGGER = LoggerUtil.getLogger(InstallResourceService.class);
    private final BooleanSupplier installTokenRequired;

    public InstallResourceService() {
        this(InstallRequestSecurity::isTokenRequired);
    }

    InstallResourceService(BooleanSupplier installTokenRequired) {
        this.installTokenRequired = installTokenRequired;
    }

    private String getFeedbackUrl(HttpRequest request) {
        return "https://blog.zrlog.com/feedback.html?v=" + Objects.requireNonNullElse(request.getServerConfig().getApplicationVersion(), "3")
                + "&os=" + System.getProperty("os.name");
    }

    public Object installResourceInfo(HttpRequest request) {
        InstallRuntimeResourceResponse response = new InstallRuntimeResourceResponse();
        response.setLang(InstallConstants.installConfig.getAcceptLanguage());
        response.setAskConfig(InstallConstants.installConfig.isAskConfig());
        response.setMissingConfig(InstallConstants.installConfig.isMissingConfig());
        response.setWarMode(InstallConstants.installConfig.isWarMode());
        response.setCurrentVersion(InstallConstants.installConfig.getBuildVersion());
        response.setCharset(Charset.defaultCharset().displayName());
        response.setRuntimeMode(new InstallProbeService().probe(InstallConstants.installConfig).getRuntimeMode());
        response.setLocalSqliteAvailable(LocalSqliteSupport.isAvailable(InstallConstants.installConfig));
        response.setInstallTokenRequired(installTokenRequired.getAsBoolean());
        InstallAction installAction = InstallConstants.installConfig.getAction();
        InstallRecoveryStore recoveryStore = new InstallRecoveryStore();
        readInstallState(response, recoveryStore, installAction);
        response.setOnlineUpgradable(InstallConstants.installConfig.getUpgradeAction().isSupported());
        response.setFeedbackUrl(getFeedbackUrl(request));
        LastVersionInfo lastVersionInfo = InstallConstants.installConfig.getLastVersionInfo();
        if (Objects.nonNull(lastVersionInfo) && Objects.equals(lastVersionInfo.getLatestVersion(), false)) {
            response.setUpgradeVersion(lastVersionInfo.getNewVersion());
            response.setUpgradeChangeLog(lastVersionInfo.getChangeLog());
            response.setUpgradeDownloadUrl(lastVersionInfo.getDownloadUrl());
        }
        return response;
    }

    private void readInstallState(InstallRuntimeResourceResponse response,
                                  InstallRecoveryStore recoveryStore,
                                  InstallAction installAction) {
        try (InstallOperationLock operationLock = InstallOperationLock.tryAcquire(installAction.getLockFile())) {
            if (operationLock == null) {
                response.setInstalled(false);
                response.setInstallRecoveryAvailable(false);
                response.setInstallOperationInProgress(true);
                return;
            }
            boolean installed = installAction.isInstalled();
            response.setInstalled(installed);
            response.setInstallOperationInProgress(false);
            if (installed) {
                recoveryStore.clear(installAction.getLockFile());
                response.setInstallRecoveryAvailable(false);
            } else {
                response.setInstallRecoveryAvailable(recoveryStore.isAvailable(
                        installAction.getLockFile()));
            }
        } catch (IOException e) {
            InstallLogUtil.logFailure(LOGGER, Level.WARNING,
                    InstallLogUtil.FailurePhase.INSTALL_STATE_READ, e);
            boolean installed = installAction.isInstalled();
            response.setInstalled(installed);
            response.setInstallRecoveryAvailable(false);
            response.setInstallOperationInProgress(false);
        }
    }
}
