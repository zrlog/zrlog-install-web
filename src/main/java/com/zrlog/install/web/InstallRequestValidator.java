package com.zrlog.install.web;

import com.zrlog.install.exception.InvalidInstallRequestException;
import com.zrlog.install.exception.MissingDbHostException;
import com.zrlog.install.exception.MissingDbNameException;
import com.zrlog.install.exception.MissingDbPortException;
import com.zrlog.install.exception.MissingDbUserNameException;
import com.zrlog.install.util.InstallI18nUtil;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class InstallRequestValidator {

    private static final Set<String> SUPPORTED_DATABASE_TYPES = Set.of("mysql", "webapi", "sqlite");
    private static final Pattern SAFE_DATABASE_NAME = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern SAFE_DATABASE_HOST = Pattern.compile("[A-Za-z0-9._:\\-\\[\\]]{1,255}");
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9]"
                    + "(?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
                    + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+");

    private InstallRequestValidator() {
    }

    public static void validateDatabase(Map<String, String> values) {
        String dbType = trimmedOrDefault(values.get("dbType"), "mysql").toLowerCase(Locale.ROOT);
        if (!SUPPORTED_DATABASE_TYPES.contains(dbType)) {
            throw invalid("validationDbType");
        }
        if ("sqlite".equals(dbType)) {
            return;
        }

        String dbHost = trimmed(values.get("dbHost"));
        if (dbHost.isEmpty()) {
            throw new MissingDbHostException();
        }
        if (!SAFE_DATABASE_HOST.matcher(dbHost).matches()) {
            throw invalid("validationDbHost");
        }

        String dbPort = trimmed(values.get("dbPort"));
        if (dbPort.isEmpty()) {
            throw new MissingDbPortException();
        }
        if (!isValidPort(dbPort)) {
            throw invalid("validationDbPort");
        }

        String dbUserName = trimmed(values.get("dbUserName"));
        if (dbUserName.isEmpty()) {
            throw new MissingDbUserNameException();
        }
        if (dbUserName.length() > 128 || containsControlCharacter(dbUserName)) {
            throw invalid("validationDbUserName");
        }

        String dbName = trimmed(values.get("dbName"));
        if (dbName.isEmpty()) {
            throw new MissingDbNameException();
        }
        if (!SAFE_DATABASE_NAME.matcher(dbName).matches()) {
            throw invalid("validationDbName");
        }

        String dbPassword = valueOrEmpty(values.get("dbPassword"));
        if (dbPassword.length() > 4096 || containsControlCharacter(dbPassword)) {
            throw invalid("validationDbPassword");
        }
    }

    public static void validateSite(Map<String, String> values) {
        String username = trimmed(values.get("username"));
        if (username.isEmpty() || username.length() > 16 || containsControlCharacter(username)) {
            throw invalid("validationAdminUsername");
        }

        String password = valueOrEmpty(values.get("password"));
        if (password.trim().isEmpty() || password.length() < 8 || password.length() > 1024
                || containsControlCharacter(password)) {
            throw invalid("validationAdminPassword");
        }

        String title = trimmed(values.get("title"));
        if (title.isEmpty() || title.length() > 255 || containsControlCharacter(title)) {
            throw invalid("validationSiteTitle");
        }

        String secondTitle = trimmed(values.get("second_title"));
        if (secondTitle.length() > 255 || containsControlCharacter(secondTitle)) {
            throw invalid("validationSiteSubtitle");
        }

        String email = trimmed(values.get("email"));
        if (!email.isEmpty() && (email.length() > 64 || !EMAIL.matcher(email).matches())) {
            throw invalid("validationAdminEmail");
        }
    }

    private static boolean isValidPort(String value) {
        if (!value.chars().allMatch(Character::isDigit)) {
            return false;
        }
        try {
            int port = Integer.parseInt(value);
            return port >= 1 && port <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }

    private static String trimmed(String value) {
        return valueOrEmpty(value).trim();
    }

    private static String trimmedOrDefault(String value, String defaultValue) {
        String trimmed = trimmed(value);
        return trimmed.isEmpty() ? defaultValue : trimmed;
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static InvalidInstallRequestException invalid(String resourceKey) {
        String message = InstallI18nUtil.getInstallStringFromRes(resourceKey);
        if (message.isEmpty()) {
            message = "Invalid installation request";
        }
        return new InvalidInstallRequestException(message);
    }
}
