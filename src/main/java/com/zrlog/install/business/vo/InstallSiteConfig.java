package com.zrlog.install.business.vo;

import com.google.gson.annotations.SerializedName;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class InstallSiteConfig {

    private String title;

    @SerializedName("second_title")
    private String secondTitle;

    private String username;
    private String password;
    private String email;
    private String installDate;
    private String secretKey;

    public static InstallSiteConfig from(Map<String, String> map) {
        InstallSiteConfig config = new InstallSiteConfig();
        if (map == null) {
            return config;
        }
        config.setTitle(map.get("title"));
        config.setSecondTitle(map.get("second_title"));
        config.setUsername(map.get("username"));
        config.setPassword(map.get("password"));
        config.setEmail(map.get("email"));
        config.setInstallDate(map.get("installDate"));
        config.setSecretKey(map.get("secretKey"));
        return config;
    }

    public Map<String, String> toMap() {
        Map<String, String> map = new LinkedHashMap<>();
        putIfPresent(map, "title", title);
        putIfPresent(map, "second_title", secondTitle);
        putIfPresent(map, "username", username);
        putIfPresent(map, "password", password);
        putIfPresent(map, "email", email);
        putIfPresent(map, "installDate", installDate);
        putIfPresent(map, "secretKey", secretKey);
        return map;
    }

    private static void putIfPresent(Map<String, String> map, String key, String value) {
        if (Objects.nonNull(value)) {
            map.put(key, value);
        }
    }

    public String secretKeyOrNew() {
        return Objects.requireNonNullElseGet(secretKey, () -> UUID.randomUUID().toString());
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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getInstallDate() {
        return installDate;
    }

    public void setInstallDate(String installDate) {
        this.installDate = installDate;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }
}
