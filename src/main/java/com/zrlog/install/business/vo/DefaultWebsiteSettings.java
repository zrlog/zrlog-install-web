package com.zrlog.install.business.vo;

import com.zrlog.install.web.config.InstallConfig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class DefaultWebsiteSettings {

    public static final String ADMIN_FIRST_USE_V4_KEY = "admin_first_use_v4";
    public static final String ADMIN_FIRST_USE_V4_PENDING = "pending";

    private String appId;
    private String title;
    private String secondTitle;
    private String language;
    private Integer rows;
    private String template;
    private Integer autoUpgradeVersion;
    private String zrlogSqlVersion;
    private Map<String, String> appendedSettings;

    public static DefaultWebsiteSettings from(InstallSiteConfig webSite, InstallConfig installConfig,
                                              Map<String, String> appendedSettings) {
        DefaultWebsiteSettings settings = new DefaultWebsiteSettings();
        InstallSiteConfig source = Objects.requireNonNullElseGet(webSite, InstallSiteConfig::new);
        settings.setAppId(UUID.randomUUID().toString());
        settings.setTitle(Objects.requireNonNullElse(source.getTitle(), ""));
        settings.setSecondTitle(Objects.requireNonNullElse(source.getSecondTitle(), ""));
        settings.setLanguage(installConfig.getAcceptLanguage());
        settings.setRows(10);
        settings.setTemplate(installConfig.defaultTemplatePath());
        settings.setAutoUpgradeVersion(86400);
        settings.setZrlogSqlVersion(installConfig.getZrLogSqlVersion());
        settings.setAppendedSettings(appendedSettings);
        return settings;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("appId", appId);
        map.put("title", title);
        map.put("second_title", secondTitle);
        map.put("language", language);
        map.put("rows", rows);
        map.put("template", template);
        map.put("autoUpgradeVersion", autoUpgradeVersion);
        map.put("zrlogSqlVersion", zrlogSqlVersion);
        if (Objects.nonNull(appendedSettings)) {
            map.putAll(appendedSettings);
        }
        map.put(ADMIN_FIRST_USE_V4_KEY, ADMIN_FIRST_USE_V4_PENDING);
        return map;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSecondTitle() {
        return secondTitle;
    }

    public void setSecondTitle(String secondTitle) {
        this.secondTitle = secondTitle;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Integer getRows() {
        return rows;
    }

    public void setRows(Integer rows) {
        this.rows = rows;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public Integer getAutoUpgradeVersion() {
        return autoUpgradeVersion;
    }

    public void setAutoUpgradeVersion(Integer autoUpgradeVersion) {
        this.autoUpgradeVersion = autoUpgradeVersion;
    }

    public String getZrlogSqlVersion() {
        return zrlogSqlVersion;
    }

    public void setZrlogSqlVersion(String zrlogSqlVersion) {
        this.zrlogSqlVersion = zrlogSqlVersion;
    }

    public Map<String, String> getAppendedSettings() {
        return appendedSettings;
    }

    public void setAppendedSettings(Map<String, String> appendedSettings) {
        this.appendedSettings = appendedSettings;
    }
}
