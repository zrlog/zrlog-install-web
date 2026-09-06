package com.zrlog.install.business.service;

import com.zrlog.install.web.config.InstallConfig;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class InstallPreflightService {

    public void assertReady(InstallConfig installConfig) throws IOException {
        assertWritableTarget(installConfig.getDbPropertiesFile(), "db.properties");
        assertWritableTarget(installConfig.getAction().getLockFile(), "install.lock");
        assertWritableTarget(InstallOperationLock.operationLockFile(
                installConfig.getAction().getLockFile()).toFile(), "install operation lock");
        assertWritableTarget(InstallRecoveryStore.recoveryFile(
                installConfig.getAction().getLockFile()), "install recovery state");
        try (InputStream inputStream = InstallPreflightService.class.getResourceAsStream("/init-table-structure.sql")) {
            if (inputStream == null) {
                throw new IOException("Missing init-table-structure.sql");
            }
        }
    }

    private void assertWritableTarget(File targetFile, String label) throws IOException {
        File parent = targetFile.getParentFile();
        if (parent == null) {
            throw new IOException("Missing parent directory for " + label);
        }
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IOException("Can not create parent directory for " + label);
        }
        if (!parent.isDirectory() || !parent.canWrite()) {
            throw new IOException("Parent directory is not writable for " + label);
        }
        if (targetFile.exists()) {
            if (!targetFile.isFile()) {
                throw new IOException(label + " is not a regular file");
            }
            if (!targetFile.canWrite()) {
                throw new IOException(label + " is not writable");
            }
        }
        Path source = Files.createTempFile(parent.toPath(), ".zrlog-install-", ".tmp");
        Path target = source.resolveSibling(source.getFileName() + ".moved");
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            throw new IOException("Atomic file updates are not supported for " + label, e);
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(target);
        }
    }
}
