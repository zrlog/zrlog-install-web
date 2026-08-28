package com.zrlog.install.web;

import com.zrlog.install.exception.InvalidInstallRequestException;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertThrows;

public class InstallRequestValidatorTest {

    @Test
    public void shouldAcceptEverySupportedDatabaseType() {
        InstallRequestValidator.validateDatabase(validDatabase("mysql"));
        InstallRequestValidator.validateDatabase(validDatabase("webapi"));
        InstallRequestValidator.validateDatabase(Map.of("dbType", "sqlite"));
    }

    @Test
    public void shouldRejectUnsupportedOrUnsafeDatabaseValues() {
        assertInvalidDatabase("dbType", "postgresql");
        assertInvalidDatabase("dbHost", "https://database.example.com/query");
        assertInvalidDatabase("dbPort", "0");
        assertInvalidDatabase("dbPort", "65536");
        assertInvalidDatabase("dbPort", "3306?allowMultiQueries=true");
        assertInvalidDatabase("dbUserName", "user\u0000name");
        assertInvalidDatabase("dbPassword", "password\u0000suffix");
        assertInvalidDatabase("dbPassword", "password\nsuffix");
        assertInvalidDatabase("dbPassword", "password\tsuffix");
        assertInvalidDatabase("dbName", "zrlog`; DROP TABLE user; --");
        assertInvalidDatabase("dbName", "a".repeat(65));
    }

    @Test
    public void shouldAcceptValidSiteValuesIncludingAnEmptyEmail() {
        InstallRequestValidator.validateSite(validSite());
        Map<String, String> noEmail = validSite();
        noEmail.put("email", "");
        InstallRequestValidator.validateSite(noEmail);
    }

    @Test
    public void shouldRejectInvalidSiteValues() {
        assertInvalidSite("username", "");
        assertInvalidSite("username", "a".repeat(17));
        assertInvalidSite("password", "short");
        assertInvalidSite("password", "password\u0000suffix");
        assertInvalidSite("password", "password\nsuffix");
        assertInvalidSite("password", "password\tsuffix");
        assertInvalidSite("title", "");
        assertInvalidSite("title", "a".repeat(256));
        assertInvalidSite("second_title", "a".repeat(256));
        assertInvalidSite("email", "not-an-email");
        assertInvalidSite("email", "admin@example.com" + "a".repeat(50));
    }

    private static void assertInvalidDatabase(String key, String value) {
        Map<String, String> values = validDatabase("mysql");
        values.put(key, value);
        assertThrows(InvalidInstallRequestException.class,
                () -> InstallRequestValidator.validateDatabase(values));
    }

    private static void assertInvalidSite(String key, String value) {
        Map<String, String> values = validSite();
        values.put(key, value);
        assertThrows(InvalidInstallRequestException.class,
                () -> InstallRequestValidator.validateSite(values));
    }

    private static Map<String, String> validDatabase(String dbType) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("dbType", dbType);
        values.put("dbHost", "database.internal");
        values.put("dbPort", "3306");
        values.put("dbUserName", "zrlog_user");
        values.put("dbPassword", "database-password");
        values.put("dbName", "zrlog_production");
        return values;
    }

    private static Map<String, String> validSite() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("username", "admin");
        values.put("password", "strong-password");
        values.put("title", "ZrLog");
        values.put("second_title", "Notes");
        values.put("email", "admin@example.com");
        return values;
    }
}
