package com.zrlog.install.business.service;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class InstallOperationLockTest {

    @Test(timeout = 20000)
    public void shouldCoordinateAcrossProcessesAndReleaseWithoutDeletingInstallMarker() throws Exception {
        Path root = Files.createTempDirectory("zrlog-install-operation-lock");
        File installLockFile = root.resolve("install.lock").toFile();
        Path readyFile = root.resolve("child.ready");
        Path releaseFile = root.resolve("child.release");
        Process child = startLockHolder(installLockFile, readyFile, releaseFile);
        try {
            waitUntilReady(child, readyFile, releaseFile);

            try (InstallOperationLock competingLock = InstallOperationLock.tryAcquire(installLockFile)) {
                assertNull(competingLock);
            }
            Files.createFile(releaseFile);
            assertTrue(child.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, child.exitValue());

            try (InstallOperationLock operationLock = InstallOperationLock.tryAcquire(installLockFile)) {
                assertNotNull(operationLock);
                Files.writeString(installLockFile.toPath(), "installed");
            }

            assertTrue(installLockFile.exists());
            assertEquals("installed", Files.readString(installLockFile.toPath()));
            assertTrue(Files.exists(InstallOperationLock.operationLockFile(installLockFile)));
        } finally {
            if (child.isAlive()) {
                child.destroyForcibly();
                child.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }

    private static Process startLockHolder(File installLockFile, Path readyFile, Path releaseFile)
            throws Exception {
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        return new ProcessBuilder(javaExecutable, "-cp", classPath,
                InstallOperationLockProcess.class.getName(), installLockFile.getAbsolutePath(),
                readyFile.toString(), releaseFile.toString()).redirectErrorStream(true).start();
    }

    private static void waitUntilReady(Process child, Path readyFile, Path releaseFile) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!Files.exists(readyFile) && child.isAlive() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue("Lock-holder process exited before acquiring the lock", child.isAlive());
        assertTrue("Lock-holder process did not acquire the lock in time", Files.exists(readyFile));
        assertFalse(Files.exists(releaseFile));
    }
}
