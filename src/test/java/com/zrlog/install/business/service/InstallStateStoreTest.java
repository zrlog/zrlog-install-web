package com.zrlog.install.business.service;

import org.junit.Test;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class InstallStateStoreTest {

    @Test
    public void shouldAtomicallyCommitPreparedStateAndKeepCompletionMarker() throws Exception {
        Path root = Files.createTempDirectory("zrlog-install-state-commit");
        Path dbProperties = root.resolve("db.properties");
        Path installLock = root.resolve("install.lock");
        InstallStateStore store = new InstallStateStore();

        try (InstallStateStore.InstallStateTransaction transaction = store.prepare(
                dbProperties.toFile(), Map.of("jdbcUrl", "jdbc:test:new"), installLock.toFile())) {
            assertFalse(Files.exists(dbProperties));
            assertFalse(Files.exists(installLock));

            transaction.commitForCallback();

            assertEquals("jdbc:test:new", readProperties(dbProperties).getProperty("jdbcUrl"));
            assertTrue(Files.exists(installLock));
            transaction.complete();
        }

        assertTrue(Files.exists(installLock));
        assertNoTransactionFiles(root);
    }

    @Test
    public void shouldRollBackOnlyStateCommittedByFailedCallback() throws Exception {
        Path root = Files.createTempDirectory("zrlog-install-state-rollback");
        Path dbProperties = root.resolve("db.properties");
        Path installLock = root.resolve("install.lock");
        byte[] previousProperties = "jdbcUrl=jdbc:test:previous\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(dbProperties, previousProperties);
        InstallStateStore store = new InstallStateStore();

        try (InstallStateStore.InstallStateTransaction transaction = store.prepare(
                dbProperties.toFile(), Map.of("jdbcUrl", "jdbc:test:new"), installLock.toFile())) {
            transaction.commitForCallback();
            assertTrue(Files.exists(installLock));
            assertEquals("jdbc:test:new", readProperties(dbProperties).getProperty("jdbcUrl"));
            // Closing without complete simulates a host callback failure.
        }

        assertFalse(Files.exists(installLock));
        assertArrayEquals(previousProperties, Files.readAllBytes(dbProperties));
        assertNoTransactionFiles(root);
    }

    @Test
    public void shouldNeverReplaceOrDeleteExistingInstallMarker() throws Exception {
        Path root = Files.createTempDirectory("zrlog-install-state-existing-lock");
        Path dbProperties = root.resolve("db.properties");
        Path installLock = root.resolve("install.lock");
        byte[] marker = "existing-install-marker".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.writeString(dbProperties, "jdbcUrl=jdbc:test:previous\n");
        Files.write(installLock, marker);

        assertThrows(FileAlreadyExistsException.class, () -> new InstallStateStore().prepare(
                dbProperties.toFile(), Map.of("jdbcUrl", "jdbc:test:new"), installLock.toFile()));

        assertArrayEquals(marker, Files.readAllBytes(installLock));
        assertEquals("jdbc:test:previous", readProperties(dbProperties).getProperty("jdbcUrl"));
        assertNoTransactionFiles(root);
    }

    @Test
    public void shouldRefuseToDeleteInstallMarkerChangedAfterCommit() throws Exception {
        Path root = Files.createTempDirectory("zrlog-install-state-marker-ownership");
        Path dbProperties = root.resolve("db.properties");
        Path installLock = root.resolve("install.lock");
        Files.writeString(dbProperties, "jdbcUrl=jdbc:test:previous\n");
        InstallStateStore.InstallStateTransaction transaction = new InstallStateStore().prepare(
                dbProperties.toFile(), Map.of("jdbcUrl", "jdbc:test:new"), installLock.toFile());
        transaction.commitForCallback();
        Files.writeString(installLock, "marker-from-another-owner");

        IOException failure = assertThrows(IOException.class, transaction::close);

        assertTrue(failure.getMessage().contains("refusing to remove"));
        assertEquals("marker-from-another-owner", Files.readString(installLock));
        assertEquals("jdbc:test:new", readProperties(dbProperties).getProperty("jdbcUrl"));
        assertNoTransactionFiles(root);
    }

    private static Properties readProperties(Path file) throws Exception {
        Properties properties = new Properties();
        try (var inputStream = Files.newInputStream(file)) {
            properties.load(inputStream);
        }
        return properties;
    }

    private static void assertNoTransactionFiles(Path root) throws Exception {
        try (var files = Files.list(root)) {
            assertFalse(files.map(path -> path.getFileName().toString()).anyMatch(name ->
                    name.startsWith(".db.properties-") || name.startsWith(".install.lock-")));
        }
    }
}
