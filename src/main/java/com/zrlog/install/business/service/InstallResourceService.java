package com.zrlog.install.business.service;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.install.business.response.InstallRuntimeResourceResponse;
import com.zrlog.install.business.response.LastVersionInfo;
import com.zrlog.install.util.InstallLogUtil;
import com.zrlog.install.web.InstallAction;
import com.zrlog.install.web.InstallRequestSecurity;
import com.zrlog.install.web.config.InstallConfig;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

@Singleton
public class InstallResourceService {

    private static final Logger LOGGER = LoggerUtil.getLogger(InstallResourceService.class);
    private final BooleanSupplier installTokenRequired;
    private final InstallConfig config;
    private final InstallProbeService probe;
    private final InstallRecoveryStore recoveryStore;

    @Inject
    public InstallResourceService(InstallConfig config, InstallProbeService probe,
                                  InstallRecoveryStore recoveryStore) {
        this(config, probe, recoveryStore, InstallRequestSecurity::isTokenRequired);
    }

    InstallResourceService(InstallConfig config, InstallProbeService probe,
                           InstallRecoveryStore recoveryStore, BooleanSupplier installTokenRequired) {
        this.config = Objects.requireNonNull(config);
        this.probe = Objects.requireNonNull(probe);
        this.recoveryStore = Objects.requireNonNull(recoveryStore);
        this.installTokenRequired = installTokenRequired;
    }

    private String getFeedbackUrl(HttpRequest request) {
        return "https://blog.zrlog.com/feedback.html?v=" + Objects.requireNonNullElse(request.getServerConfig().getApplicationVersion(), "3")
                + "&os=" + System.getProperty("os.name");
    }

    public Object installResourceInfo(HttpRequest request) {
        InstallRuntimeResourceResponse response = new InstallRuntimeResourceResponse();
        response.setLang(config.getAcceptLanguage());
        response.setAskConfig(config.isAskConfig());
        response.setMissingConfig(config.isMissingConfig());
        response.setWarMode(config.isWarMode());
        response.setCurrentVersion(config.getBuildVersion());
        response.setCharset(Charset.defaultCharset().displayName());
        response.setRuntimeMode(probe.probe(config).getRuntimeMode());
        response.setLocalSqliteAvailable(LocalSqliteSupport.isAvailable(config));
        response.setInstallTokenRequired(installTokenRequired.getAsBoolean());
        InstallAction installAction = config.getAction();
        readInstallState(response, recoveryStore, installAction);
        response.setOnlineUpgradable(config.getUpgradeAction().isSupported());
        response.setFeedbackUrl(getFeedbackUrl(request));
        LastVersionInfo lastVersionInfo = config.getLastVersionInfo();
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
