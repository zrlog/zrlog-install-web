package com.zrlog.install.business.service;

import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
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

    @Test
    public void shouldUseTheDatabaseConfigurationDirectoryForSqliteData() throws Exception {
        Path root = Files.createTempDirectory("zrlog-local-sqlite-path");
        Path configFile = root.resolve("conf/db.properties");
        FakeInstallConfig config = new FakeInstallConfig(
                configFile.toFile(), root.resolve("conf/install.lock").toFile());

        assertTrue(LocalSqliteSupport.isAvailable(config));
        assertEquals(root.resolve("conf/zrlog.db").toAbsolutePath().normalize().toFile(),
                LocalSqliteSupport.getDatabaseFile(config));
        assertTrue(LocalSqliteSupport.createDatabaseConfig(config).getJdbcUrl().startsWith(
                "jdbc:sqlite:" + root.resolve("conf/zrlog.db").toAbsolutePath().normalize()));
    }

    @Test
    public void shouldRejectInvalidConfigTargetsAndLeaveOccupiedDatabaseForInstallConflictHandling() throws Exception {
        Path root = Files.createTempDirectory("zrlog-local-sqlite-unavailable");
        Path parentBlocker = Files.createFile(root.resolve("not-a-directory"));
        FakeInstallConfig blockedParent = new FakeInstallConfig(
                parentBlocker.resolve("db.properties").toFile(),
                parentBlocker.resolve("install.lock").toFile());
        assertFalse(LocalSqliteSupport.isAvailable(blockedParent));

        Path configDirectory = Files.createDirectories(root.resolve("conf/db.properties"));
        FakeInstallConfig blockedConfigFile = new FakeInstallConfig(
                configDirectory.toFile(), root.resolve("conf/install.lock").toFile());
        assertFalse(LocalSqliteSupport.isAvailable(blockedConfigFile));

        Path dataDirectory = Files.createDirectories(root.resolve("data/conf/zrlog.db"));
        FakeInstallConfig blockedDatabaseFile = new FakeInstallConfig(
                dataDirectory.getParent().resolve("db.properties").toFile(),
                dataDirectory.getParent().resolve("install.lock").toFile());
        assertTrue(LocalSqliteSupport.isAvailable(blockedDatabaseFile));
        assertTrue(LocalSqliteSupport.databaseTargetExists(blockedDatabaseFile));
    }

    @Test
    public void shouldReserveDatabaseExclusivelyWithOwnerOnlyPermissions() throws Exception {
        Path root = Files.createTempDirectory("zrlog-local-sqlite-reservation");
        FakeInstallConfig config = new FakeInstallConfig(
                root.resolve("conf/db.properties").toFile(),
                root.resolve("conf/install.lock").toFile());

        try (LocalSqliteSupport.DatabaseReservation reservation =
                     LocalSqliteSupport.reserveDatabaseFile(config)) {
            reservation.preserve();
        }

        Path databaseFile = LocalSqliteSupport.getDatabaseFile(config).toPath();
        assertTrue(Files.isRegularFile(databaseFile, LinkOption.NOFOLLOW_LINKS));
        if (Files.getFileStore(databaseFile).supportsFileAttributeView(PosixFileAttributeView.class)) {
            assertEquals(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(databaseFile, LinkOption.NOFOLLOW_LINKS));
        }
        assertThrows(FileAlreadyExistsException.class,
                () -> LocalSqliteSupport.reserveDatabaseFile(config));
    }

    @Test
    public void shouldReleaseAnUnusedDatabaseReservationForRetry() throws Exception {
        Path root = Files.createTempDirectory("zrlog-local-sqlite-unused-reservation");
        FakeInstallConfig config = new FakeInstallConfig(
                root.resolve("conf/db.properties").toFile(),
                root.resolve("conf/install.lock").toFile());

        try (LocalSqliteSupport.DatabaseReservation ignored =
                     LocalSqliteSupport.reserveDatabaseFile(config)) {
            assertTrue(LocalSqliteSupport.databaseTargetExists(config));
        }

        assertFalse(LocalSqliteSupport.databaseTargetExists(config));
        try (LocalSqliteSupport.DatabaseReservation retry =
                     LocalSqliteSupport.reserveDatabaseFile(config)) {
            retry.preserve();
        }
        assertTrue(LocalSqliteSupport.databaseTargetExists(config));
    }

    @Test
    public void shouldOnlyDeleteAReservationWhenItsNonNullIdentityStillMatches() {
        Object identity = new Object();

        assertTrue(LocalSqliteSupport.isSameReservationTarget(identity, identity, true));
        assertFalse(LocalSqliteSupport.isSameReservationTarget(null, null, true));
        assertFalse(LocalSqliteSupport.isSameReservationTarget(identity, new Object(), true));
        assertFalse(LocalSqliteSupport.isSameReservationTarget(identity, identity, false));
    }

    @Test(timeout = 20000)
    public void shouldAllowOnlyOneProcessToReserveTheDatabaseTarget() throws Exception {
        Path root = Files.createTempDirectory("zrlog-local-sqlite-process-reservation");
        Path configDirectory = Files.createDirectories(root.resolve("conf"));
        Path startFile = root.resolve("start");
        Path firstReady = root.resolve("first.ready");
        Path secondReady = root.resolve("second.ready");
        Path firstResult = root.resolve("first.result");
        Path secondResult = root.resolve("second.result");
        Process first = startReservationProcess(configDirectory, firstReady, startFile, firstResult);
        Process second = startReservationProcess(configDirectory, secondReady, startFile, secondResult);
        try {
            waitUntilReady(List.of(first, second), List.of(firstReady, secondReady));
            Files.createFile(startFile);
            assertProcessSuccess(first);
            assertProcessSuccess(second);

            List<String> results = List.of(
                    Files.readString(firstResult, StandardCharsets.US_ASCII),
                    Files.readString(secondResult, StandardCharsets.US_ASCII));
            assertEquals(1L, results.stream().filter("CREATED"::equals).count());
            assertEquals(1L, results.stream().filter("EXISTS"::equals).count());
            assertTrue(Files.isRegularFile(configDirectory.resolve("zrlog.db"),
                    LinkOption.NOFOLLOW_LINKS));
        } finally {
            destroy(first);
            destroy(second);
        }
    }

    private static Process startReservationProcess(Path configDirectory, Path readyFile,
                                                   Path startFile, Path resultFile) throws IOException {
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        return new ProcessBuilder(javaExecutable, "-cp", classPath,
                LocalSqliteReservationProcess.class.getName(),
                configDirectory.resolve("db.properties").toString(),
                configDirectory.resolve("install.lock").toString(), readyFile.toString(),
                startFile.toString(), resultFile.toString()).redirectErrorStream(true).start();
    }

    private static void waitUntilReady(List<Process> processes, List<Path> readyFiles) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while ((!Files.exists(readyFiles.get(0)) || !Files.exists(readyFiles.get(1)))
                && processes.stream().allMatch(Process::isAlive) && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue("Reservation processes did not become ready in time",
                Files.exists(readyFiles.get(0)) && Files.exists(readyFiles.get(1)));
    }

    private static void assertProcessSuccess(Process process) throws Exception {
        assertTrue("Reservation process did not finish", process.waitFor(10, TimeUnit.SECONDS));
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(output, 0, process.exitValue());
    }

    private static void destroy(Process process) throws Exception {
        if (process.isAlive()) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }
    }
}
