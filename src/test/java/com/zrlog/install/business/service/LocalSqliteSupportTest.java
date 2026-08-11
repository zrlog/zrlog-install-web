package com.zrlog.install.business.service;

import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LocalSqliteSupportTest {

    @After
    public void tearDown() {
        System.clearProperty("org.graalvm.nativeimage.imagecode");
    }

    @Test
    public void shouldAllowLocalSqliteForJvmAndNativePackagesWithTheDriver() throws Exception {
        File root = Files.createTempDirectory("zrlog-local-sqlite-support").toFile();
        FakeInstallConfig config = new FakeInstallConfig(
                new File(root, "db.properties"), new File(root, "install.lock"));

        assertTrue(LocalSqliteSupport.isAvailable(config));

        config.setWarMode(true);
        assertFalse(LocalSqliteSupport.isAvailable(config));

        config.setWarMode(false);
        System.setProperty("org.graalvm.nativeimage.imagecode", "runtime");
        assertTrue(LocalSqliteSupport.isAvailable(config));
    }

    @Test
    public void shouldKeepLocalSqliteDisabledForPackagesWithoutPersistentLocalStorage() {
        assertTrue(LocalSqliteSupport.isSupportedRuntimeMode("zip"));
        assertTrue(LocalSqliteSupport.isSupportedRuntimeMode("native"));
        assertFalse(LocalSqliteSupport.isSupportedRuntimeMode("war"));
        assertFalse(LocalSqliteSupport.isSupportedRuntimeMode("faas"));
        assertFalse(LocalSqliteSupport.isSupportedRuntimeMode("docker"));
    }
}
