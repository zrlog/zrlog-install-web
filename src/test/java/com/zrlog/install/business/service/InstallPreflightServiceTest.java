package com.zrlog.install.business.service;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.Test;

public class InstallPreflightServiceTest {

    @Test
    public void shouldPassWhenConfigTargetsAreCreatableAndSqlResourceExists() throws Exception {
        File root = Files.createTempDirectory("zrlog-install-preflight").toFile();
        FakeInstallConfig config =
                new FakeInstallConfig(
                        new File(root, "conf/db.properties"), new File(root, "conf/install.lock"));

        new InstallPreflightService().assertReady(config);

        assertTrue(new File(root, "conf").isDirectory());
    }

    @Test(expected = java.io.IOException.class)
    public void shouldRejectTargetWithoutParentDirectory() throws Exception {
        FakeInstallConfig config =
                new FakeInstallConfig(new File("db.properties"), new File("install.lock"));

        new InstallPreflightService().assertReady(config);
    }

    @Test(expected = java.io.IOException.class)
    public void shouldRejectExistingTargetThatIsNotWritable() throws Exception {
        Path root = Files.createTempDirectory("zrlog-install-preflight-readonly");
        Path dbProperties = root.resolve("db.properties");
        Files.writeString(dbProperties, "");
        try {
            Files.setPosixFilePermissions(dbProperties, Set.of(PosixFilePermission.OWNER_READ));
        } catch (UnsupportedOperationException e) {
            throw new java.io.IOException("POSIX file permissions are unavailable", e);
        }
        try {
            FakeInstallConfig config =
                    new FakeInstallConfig(
                            dbProperties.toFile(), root.resolve("install.lock").toFile());

            new InstallPreflightService().assertReady(config);
        } finally {
            Files.setPosixFilePermissions(
                    dbProperties,
                    Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        }
    }
}
