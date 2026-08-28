package com.zrlog.install.util;

import com.zrlog.install.web.InstallConstants;
import com.zrlog.install.web.config.DefaultInstallConfig;
import com.zrlog.install.web.config.InstallConfig;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InstallI18nUtilTest {

    @After
    public void tearDown() {
        InstallConstants.installConfig = new DefaultInstallConfig();
    }

    @Test
    public void shouldReturnSortedLanguageResourceMap() {
        InstallConstants.installConfig = installConfig("zh_CN");

        Map<String, Object> installMap = InstallI18nUtil.getInstallMap();

        assertTrue(installMap.containsKey("helloWorld"));
        assertEquals(installMap, InstallI18nUtil.getInstallMap());
        assertEquals(InstallI18nUtil.getInstallStringFromRes("helloWorld"), installMap.get("helloWorld"));
    }

    @Test
    public void shouldReturnEmptyValuesForUnknownLanguageOrKey() {
        InstallConstants.installConfig = installConfig("missing");

        assertTrue(InstallI18nUtil.getInstallMap().isEmpty());
        assertEquals("", InstallI18nUtil.getInstallStringFromRes("helloWorld"));
    }

    @Test
    public void shouldNotLogResourceFailureMessagesOrPaths() throws Exception {
        String sensitiveMarker = "/private/i18n/do-not-expose.properties?password=do-not-expose";
        InputStream failingInput = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException(sensitiveMarker);
            }

            @Override
            public void close() throws IOException {
                throw new IOException("close-" + sensitiveMarker);
            }
        };
        Method loadI18n = InstallI18nUtil.class.getDeclaredMethod(
                "loadI18N", InputStream.class, String.class);
        loadI18n.setAccessible(true);

        try (LogCaptureSupport logs = LogCaptureSupport.capture(InstallI18nUtil.class)) {
            loadI18n.invoke(null, failingInput, "install_failure.properties");

            assertTrue(logs.text().contains("phase=i18n-resource-load"));
            assertTrue(logs.text().contains("phase=i18n-resource-close"));
            assertTrue(logs.text().contains("exception=java.io.IOException"));
            assertFalse(logs.text().contains("do-not-expose"));
            assertFalse(logs.text().contains("/private/i18n"));
            assertFalse(logs.hasThrown());
        }
    }

    private static InstallConfig installConfig(String acceptLanguage) {
        return (InstallConfig) Proxy.newProxyInstance(
                InstallI18nUtilTest.class.getClassLoader(),
                new Class[]{InstallConfig.class},
                (proxy, method, args) -> {
                    if ("getAcceptLanguage".equals(method.getName())) {
                        return acceptLanguage;
                    }
                    if ("isWarMode".equals(method.getName())) {
                        return false;
                    }
                    if ("toString".equals(method.getName())) {
                        return "InstallConfigProxy";
                    }
                    return null;
                });
    }
}
