package com.zrlog.install.web.config;

import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.install.business.response.InstallApiResponses;
import com.zrlog.install.business.response.LastVersionInfo;
import com.zrlog.install.exception.InstallException;
import com.zrlog.install.business.type.TestConnectDbResult;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DefaultInstallConfigTest {

    @Test
    public void shouldExposeDefaultInstallSettings() {
        DefaultInstallConfig config = new DefaultInstallConfig();

        assertFalse(config.isWarMode());
        assertEquals("zh_CN", config.getAcceptLanguage());
        assertEquals("/include/templates/hexo-theme-fluid", config.defaultTemplatePath());
        assertEquals("25", config.getZrLogSqlVersion());
        assertEquals("1.0.0-SNAPSHOT", config.getBuildVersion());
        assertTrue(config.isAskConfig());
        assertTrue(config.isMissingConfig());
        assertNotNull(config.getAction());
        assertNotNull(config.getDbPropertiesFile());
    }

    @Test
    public void shouldAllowDefaultInstallActionSuccessCallback() {
        new DefaultInstallAction().installSuccess();
    }

    @Test
    public void shouldReturnDefaultLastVersionInfo() {
        LastVersionInfo info = new DefaultInstallConfig().getLastVersionInfo();

        assertFalse(info.getLatestVersion());
        assertEquals("3.2.0", info.getNewVersion());
        assertEquals("https://dl.zrlog.com/release/zrlog.zip", info.getDownloadUrl());
        assertTrue(info.getChangeLog().contains("Change Log"));
    }

    @Test
    public void shouldReturnMysqlJdbcQueryOnlyForMysql() {
        DefaultInstallConfig config = new DefaultInstallConfig();

        assertEquals("characterEncoding=utf8&allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=GMT",
                config.getJdbcUrlQueryStr("mysql", Collections.emptyMap()));
        assertEquals("", config.getJdbcUrlQueryStr("sqlite", Collections.emptyMap()));
    }

    @Test
    public void shouldHashPasswordBeforeStoring() {
        String encrypted = new DefaultInstallConfig().encryptPassword("password");

        assertNotEquals("password", encrypted);
        assertTrue(encrypted.startsWith("pbkdf2_sha256$"));
    }

    @Test
    public void shouldRenderInstallErrorAsJson() {
        AtomicReference<Object> rendered = new AtomicReference<>();
        HttpResponse response = response(rendered);

        new DefaultInstallConfig().getErrorHandler().doHandle(null, response,
                new InstallException(TestConnectDbResult.MISSING_JDBC_DRIVER));

        InstallApiResponses.Error error = (InstallApiResponses.Error) rendered.get();
        assertEquals(Integer.valueOf(9000), error.getError());
        assertEquals("MISSING_JDBC_DRIVER", error.getCode());
        assertTrue(error.getMessage().contains("[Error-MISSING_JDBC_DRIVER]"));
    }

    @Test
    public void shouldRenderUnexpectedErrorAsJson() {
        AtomicReference<Object> rendered = new AtomicReference<>();
        HttpResponse response = response(rendered);

        new DefaultInstallConfig().getErrorHandler().doHandle(null, response,
                new IllegalStateException("boom"));

        InstallApiResponses.Error error = (InstallApiResponses.Error) rendered.get();
        assertEquals(Integer.valueOf(9999), error.getError());
        assertEquals("boom", error.getMessage());
    }

    private static HttpResponse response(AtomicReference<Object> rendered) {
        return (HttpResponse) Proxy.newProxyInstance(
                DefaultInstallConfigTest.class.getClassLoader(),
                new Class[]{HttpResponse.class},
                (proxy, method, args) -> {
                    if ("renderJson".equals(method.getName())) {
                        rendered.set(args[0]);
                        return null;
                    }
                    if ("getHeader".equals(method.getName())) {
                        return Collections.emptyMap();
                    }
                    if ("toString".equals(method.getName())) {
                        return "HttpResponseProxy";
                    }
                    return null;
                });
    }
}
