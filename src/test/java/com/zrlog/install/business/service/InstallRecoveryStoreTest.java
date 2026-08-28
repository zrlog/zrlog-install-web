package com.zrlog.install.business.service;

import com.zrlog.install.business.vo.InstallDatabaseConfig;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InstallRecoveryStoreTest {

    @Test
    public void shouldAtomicallyStoreLoadAndClearPrivateRecoveryConfig() throws Exception {
        Path root = Files.createTempDirectory("zrlog-install-recovery");
        Path installLock = root.resolve("install.lock");
        InstallDatabaseConfig databaseConfig = InstallDatabaseConfig.from(Map.of(
                "dbType", "mysql",
                "dbHost", "database.internal",
                "dbPort", "3306",
                "dbName", "zrlog",
                "user", "zrlog",
                "password", "recovery-secret",
                "driverClass", "com.mysql.cj.jdbc.Driver",
                "jdbcUrl", "jdbc:mysql://database.internal:3306/zrlog"));
        InstallRecoveryStore store = new InstallRecoveryStore();

        store.save(installLock.toFile(), databaseConfig);

        assertTrue(store.isAvailable(installLock.toFile()));
        InstallDatabaseConfig loaded = store.load(installLock.toFile()).orElseThrow();
        assertEquals(databaseConfig.toMap(), loaded.toMap());
        String recoveryText = Files.readString(
                InstallRecoveryStore.recoveryFile(installLock.toFile()).toPath(), StandardCharsets.ISO_8859_1);
        assertFalse(recoveryText.contains("installToken"));
        try {
            assertEquals(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(
                            InstallRecoveryStore.recoveryFile(installLock.toFile()).toPath()));
        } catch (UnsupportedOperationException ignored) {
            // POSIX permissions are not available on every supported platform.
        }

        store.clear(installLock.toFile());
        assertFalse(store.isAvailable(installLock.toFile()));
    }

    @Test
    public void shouldIgnoreMalformedOrSymlinkedRecoveryState() throws Exception {
        Path root = Files.createTempDirectory("zrlog-install-recovery-invalid");
        Path installLock = root.resolve("install.lock");
        Path recoveryFile = InstallRecoveryStore.recoveryFile(installLock.toFile()).toPath();
        Files.writeString(recoveryFile, "recoveryVersion=unsupported\n");
        InstallRecoveryStore store = new InstallRecoveryStore();

        assertFalse(store.isAvailable(installLock.toFile()));
        assertTrue(store.load(installLock.toFile()).isEmpty());

        Files.delete(recoveryFile);
        Path target = root.resolve("outside.properties");
        Files.writeString(target, "recoveryVersion=1\n");
        try {
            Files.createSymbolicLink(recoveryFile, target);
            assertFalse(store.isAvailable(installLock.toFile()));
            assertTrue(store.load(installLock.toFile()).isEmpty());
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException ignored) {
            // Symlinks are not available on every supported platform.
        }
    }

    @Test
    public void shouldRejectOversizedOrMismatchedRecoveryConfig() throws Exception {
        Path root = Files.createTempDirectory("zrlog-install-recovery-bounds");
        Path installLock = root.resolve("install.lock");
        Path recoveryFile = InstallRecoveryStore.recoveryFile(installLock.toFile()).toPath();
        InstallRecoveryStore store = new InstallRecoveryStore();

        Files.writeString(recoveryFile, "recoveryVersion=1\ndbType=mysql\n"
                + "driverClass=com.mysql.cj.jdbc.Driver\njdbcUrl=jdbc:sqlite:test.db\n");
        assertFalse(store.isAvailable(installLock.toFile()));

        byte[] oversized = new byte[64 * 1024 + 1];
        java.util.Arrays.fill(oversized, (byte) 'a');
        Files.write(recoveryFile, oversized);
        assertFalse(store.isAvailable(installLock.toFile()));
    }
}
