package com.zrlog.install.util;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InstallI18nUtilTest {

    @Test
    public void shouldReturnSortedLanguageResourceMap() {

        Map<String, Object> installMap = InstallI18nUtil.getInstallMap("zh_CN");

        assertTrue(installMap.containsKey("helloWorld"));
        assertEquals(installMap, InstallI18nUtil.getInstallMap("zh_CN"));
        assertEquals(InstallI18nUtil.getInstallStringFromRes("helloWorld", "zh_CN"), installMap.get("helloWorld"));
    }

    @Test
    public void shouldReturnEmptyValuesForUnknownLanguageOrKey() {

        assertTrue(InstallI18nUtil.getInstallMap("missing").isEmpty());
        assertEquals("", InstallI18nUtil.getInstallStringFromRes("helloWorld", "missing"));
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

}
