package com.zrlog.install.business.service;

import com.google.gson.Gson;
import com.zrlog.install.business.response.InstallProbeData;
import com.zrlog.install.business.response.InstallProbeItem;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InstallProbeServiceTest {

    @After
    public void tearDown() {
        System.clearProperty("org.graalvm.nativeimage.imagecode");
    }

    @Test
    public void shouldProbeWritableZipRuntime() throws Exception {
        File root = Files.createTempDirectory("zrlog-install-probe").toFile();
        FakeInstallConfig config = new FakeInstallConfig(
                new File(root, "db.properties"),
                new File(root, "install.lock"));

        InstallProbeData data = new InstallProbeService().probe(config);

        assertEquals("zip", data.getRuntimeMode());
        assertEquals("pass", data.getStatus());
        assertEquals("file-creatable", findItem(data, "file.dbProperties").getValue());
        String json = new Gson().toJson(data);
        assertFalse(json.contains(root.getAbsolutePath()));
        assertFalse(json.contains("dbPropertiesPath"));
        assertFalse(json.contains("lockFilePath"));
        assertFalse(json.contains("\"path\""));
    }

    @Test
    public void shouldReportWarRuntimeBeforeNativeRuntime() throws Exception {
        System.setProperty("org.graalvm.nativeimage.imagecode", "runtime");
        FakeInstallConfig config = new FakeInstallConfig(
                File.createTempFile("zrlog-db", ".properties"),
                File.createTempFile("zrlog-lock", ".lock"));
        config.setWarMode(true);

        assertEquals("war", InstallProbeService.getRuntimeMode(config));
    }

    @Test
    public void shouldReportNativeRuntimeWhenNativeImagePropertyExists() throws Exception {
        System.setProperty("org.graalvm.nativeimage.imagecode", "runtime");
        FakeInstallConfig config = new FakeInstallConfig(
                File.createTempFile("zrlog-db", ".properties"),
                File.createTempFile("zrlog-lock", ".lock"));

        assertEquals("native", InstallProbeService.getRuntimeMode(config));
    }

    @Test
    public void shouldBlockFileProbeWhenTargetHasNoParent() {
        FakeInstallConfig config = new FakeInstallConfig(new File("db.properties"), new File("install.lock"));

        InstallProbeData data = new InstallProbeService().probe(config);

        assertEquals("block", data.getStatus());
        assertEquals("parent-not-writable", findItem(data, "file.dbProperties").getValue());
    }

    @Test
    public void shouldBlockExistingFileWhenItIsNotWritable() throws Exception {
        Path root = Files.createTempDirectory("zrlog-install-probe-readonly");
        Path dbProperties = root.resolve("db.properties");
        Files.writeString(dbProperties, "");
        try {
            Files.setPosixFilePermissions(dbProperties, Set.of(PosixFilePermission.OWNER_READ));
        } catch (UnsupportedOperationException e) {
            return;
        }
        try {
            FakeInstallConfig config = new FakeInstallConfig(
                    dbProperties.toFile(),
                    root.resolve("install.lock").toFile());

            InstallProbeData data = new InstallProbeService().probe(config);

            assertEquals("block", data.getStatus());
            assertEquals("file-not-writable", findItem(data, "file.dbProperties").getValue());
        } finally {
            Files.setPosixFilePermissions(dbProperties, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
        }
    }

    private static InstallProbeItem findItem(InstallProbeData data, String code) {
        Optional<InstallProbeItem> item = data.getItems().stream()
                .filter(e -> code.equals(e.getCode()))
                .findFirst();
        assertTrue(item.isPresent());
        return item.get();
    }
}
