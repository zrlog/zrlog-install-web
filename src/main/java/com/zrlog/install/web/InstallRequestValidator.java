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

    public static void validateDatabase(Map<String, String> values, String language) {
        String dbType = trimmedOrDefault(values.get("dbType"), "mysql").toLowerCase(Locale.ROOT);
        if (!SUPPORTED_DATABASE_TYPES.contains(dbType)) {
            throw invalid("validationDbType", language);
        }
        if ("sqlite".equals(dbType)) {
            return;
        }

        String dbHost = trimmed(values.get("dbHost"));
        if (dbHost.isEmpty()) {
            throw new MissingDbHostException(language);
        }
        if (!SAFE_DATABASE_HOST.matcher(dbHost).matches()) {
            throw invalid("validationDbHost", language);
        }

        String dbPort = trimmed(values.get("dbPort"));
        if (dbPort.isEmpty()) {
            throw new MissingDbPortException(language);
        }
        if (!isValidPort(dbPort)) {
            throw invalid("validationDbPort", language);
        }

        String dbUserName = trimmed(values.get("dbUserName"));
        if (dbUserName.isEmpty()) {
            throw new MissingDbUserNameException(language);
        }
        if (dbUserName.length() > 128 || containsControlCharacter(dbUserName)) {
            throw invalid("validationDbUserName", language);
        }

        String dbName = trimmed(values.get("dbName"));
        if (dbName.isEmpty()) {
            throw new MissingDbNameException(language);
        }
        if (!SAFE_DATABASE_NAME.matcher(dbName).matches()) {
            throw invalid("validationDbName", language);
        }

        String dbPassword = valueOrEmpty(values.get("dbPassword"));
        if (dbPassword.length() > 4096 || containsControlCharacter(dbPassword)) {
            throw invalid("validationDbPassword", language);
        }
    }

    public static void validateSite(Map<String, String> values, String language) {
        String username = trimmed(values.get("username"));
        if (username.isEmpty() || username.length() > 16 || containsControlCharacter(username)) {
            throw invalid("validationAdminUsername", language);
        }

        String password = valueOrEmpty(values.get("password"));
        if (password.trim().isEmpty() || password.length() < 8 || password.length() > 1024
                || containsControlCharacter(password)) {
            throw invalid("validationAdminPassword", language);
        }

        String title = trimmed(values.get("title"));
        if (title.isEmpty() || title.length() > 255 || containsControlCharacter(title)) {
            throw invalid("validationSiteTitle", language);
        }

        String secondTitle = trimmed(values.get("second_title"));
        if (secondTitle.length() > 255 || containsControlCharacter(secondTitle)) {
            throw invalid("validationSiteSubtitle", language);
        }

        String email = trimmed(values.get("email"));
        if (!email.isEmpty() && (email.length() > 64 || !EMAIL.matcher(email).matches())) {
            throw invalid("validationAdminEmail", language);
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

    private static InvalidInstallRequestException invalid(String resourceKey, String language) {
        String message = InstallI18nUtil.getInstallStringFromRes(resourceKey, language);
        if (message.isEmpty()) {
            message = "Invalid installation request";
        }
        return new InvalidInstallRequestException(message);
    }
}
