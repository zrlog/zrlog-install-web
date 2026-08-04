package com.zrlog.install.business.response;

public class InstallRuntimeResourceResponse {

    private Boolean installed;
    private Boolean askConfig;
    private Boolean missingConfig;
    private Boolean warMode;
    private String lang;
    private String currentVersion;
    private String installSuccessContent;
    private String upgradeVersion;
    private String upgradeChangeLog;
    private String upgradeDownloadUrl;
    private Boolean onlineUpgradable;
    private String feedbackUrl;
    private String charset;
    private String runtimeMode;
    private String dbPropertiesPath;
    private String lockFilePath;

    public Boolean getInstalled() {
        return installed;
    }

    public void setInstalled(Boolean installed) {
        this.installed = installed;
    }

    public Boolean getAskConfig() {
        return askConfig;
    }

    public void setAskConfig(Boolean askConfig) {
        this.askConfig = askConfig;
    }

    public Boolean getMissingConfig() {
        return missingConfig;
    }

    public void setMissingConfig(Boolean missingConfig) {
        this.missingConfig = missingConfig;
    }

    public Boolean getWarMode() {
        return warMode;
    }

    public void setWarMode(Boolean warMode) {
        this.warMode = warMode;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    public String getInstallSuccessContent() {
        return installSuccessContent;
    }

    public void setInstallSuccessContent(String installSuccessContent) {
        this.installSuccessContent = installSuccessContent;
    }

    public String getUpgradeVersion() {
        return upgradeVersion;
    }

    public void setUpgradeVersion(String upgradeVersion) {
        this.upgradeVersion = upgradeVersion;
    }

    public String getUpgradeChangeLog() {
        return upgradeChangeLog;
    }

    public void setUpgradeChangeLog(String upgradeChangeLog) {
        this.upgradeChangeLog = upgradeChangeLog;
    }

    public String getUpgradeDownloadUrl() {
        return upgradeDownloadUrl;
    }

    public void setUpgradeDownloadUrl(String upgradeDownloadUrl) {
        this.upgradeDownloadUrl = upgradeDownloadUrl;
    }

    public Boolean getOnlineUpgradable() {
        return onlineUpgradable;
    }

    public void setOnlineUpgradable(Boolean onlineUpgradable) {
        this.onlineUpgradable = onlineUpgradable;
    }

    public String getFeedbackUrl() {
        return feedbackUrl;
    }

    public void setFeedbackUrl(String feedbackUrl) {
        this.feedbackUrl = feedbackUrl;
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public String getRuntimeMode() {
        return runtimeMode;
    }

    public void setRuntimeMode(String runtimeMode) {
        this.runtimeMode = runtimeMode;
    }

    public String getDbPropertiesPath() {
        return dbPropertiesPath;
    }

    public void setDbPropertiesPath(String dbPropertiesPath) {
        this.dbPropertiesPath = dbPropertiesPath;
    }

    public String getLockFilePath() {
        return lockFilePath;
    }

    public void setLockFilePath(String lockFilePath) {
        this.lockFilePath = lockFilePath;
    }
}
