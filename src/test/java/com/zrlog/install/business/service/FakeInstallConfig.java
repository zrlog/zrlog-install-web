package com.zrlog.install.business.service;

import com.hibegin.http.server.api.HttpErrorHandle;
import com.zrlog.install.business.response.LastVersionInfo;
import com.zrlog.install.web.InstallAction;
import com.zrlog.install.web.config.InstallConfig;

import java.io.File;
import java.util.Map;

class FakeInstallConfig implements InstallConfig {

    private final File dbPropertiesFile;
    private final File lockFile;
    private boolean warMode;
    private boolean installed;
    private boolean askConfig;
    private boolean missingConfig;
    private String acceptLanguage = "zh_CN";
    private String buildVersion = "test";
    private LastVersionInfo lastVersionInfo;

    FakeInstallConfig(File dbPropertiesFile, File lockFile) {
        this.dbPropertiesFile = dbPropertiesFile;
        this.lockFile = lockFile;
    }

    void setWarMode(boolean warMode) {
        this.warMode = warMode;
    }

    void setInstalled(boolean installed) {
        this.installed = installed;
    }

    void setAskConfig(boolean askConfig) {
        this.askConfig = askConfig;
    }

    void setMissingConfig(boolean missingConfig) {
        this.missingConfig = missingConfig;
    }

    void setAcceptLanguage(String acceptLanguage) {
        this.acceptLanguage = acceptLanguage;
    }

    void setBuildVersion(String buildVersion) {
        this.buildVersion = buildVersion;
    }

    void setLastVersionInfo(LastVersionInfo lastVersionInfo) {
        this.lastVersionInfo = lastVersionInfo;
    }

    @Override
    public InstallAction getAction() {
        return new InstallAction() {
            @Override
            public void installSuccess() {
            }

            @Override
            public File getLockFile() {
                return lockFile;
            }

            @Override
            public boolean isInstalled() {
                return installed;
            }
        };
    }

    @Override
    public boolean isWarMode() {
        return warMode;
    }

    @Override
    public String getAcceptLanguage() {
        return acceptLanguage;
    }

    @Override
    public String encryptPassword(String password) {
        return password;
    }

    @Override
    public String defaultTemplatePath() {
        return "/include/templates/default";
    }

    @Override
    public String getZrLogSqlVersion() {
        return "26";
    }

    @Override
    public File getDbPropertiesFile() {
        return dbPropertiesFile;
    }

    @Override
    public LastVersionInfo getLastVersionInfo() {
        return lastVersionInfo;
    }

    @Override
    public String getBuildVersion() {
        return buildVersion;
    }

    @Override
    public String getJdbcUrlQueryStr(String dbType, Map<String, String[]> paramMap) {
        return "";
    }

    @Override
    public HttpErrorHandle getErrorHandler() {
        return null;
    }

    @Override
    public boolean isAskConfig() {
        return askConfig;
    }

    @Override
    public boolean isMissingConfig() {
        return missingConfig;
    }
}
