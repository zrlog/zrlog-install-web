package com.zrlog.install.business.service;

import com.hibegin.common.util.LoggerUtil;
import com.zrlog.install.util.InstallLogUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

final class InstallStateStore {

    private static final Logger LOGGER = LoggerUtil.getLogger(InstallStateStore.class);
    private static final String DB_PROPERTIES_COMMENT = "This is a database configuration dbFile";

    InstallStateTransaction prepare(File dbPropertiesFile, Map<String, String> values,
                                    File installLockFile) throws IOException {
        Path dbProperties = dbPropertiesFile.toPath().toAbsolutePath().normalize();
        Path installLock = installLockFile.toPath().toAbsolutePath().normalize();
        Path dbParent = requireParent(dbProperties, "db.properties");
        Path lockParent = requireParent(installLock, "install.lock");
        Files.createDirectories(dbParent);
        Files.createDirectories(lockParent);
        if (Files.exists(installLock, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException(installLock.toString());
        }

        Path stagedProperties = null;
        Path previousProperties = null;
        Path stagedInstallLock = null;
        try {
            stagedProperties = stageProperties(dbParent, values);
            if (Files.exists(dbProperties, LinkOption.NOFOLLOW_LINKS)) {
                previousProperties = Files.createTempFile(dbParent, ".db.properties-backup-", ".tmp");
                Files.copy(dbProperties, previousProperties, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
                force(previousProperties);
            }
            byte[] installMarker = ("zrlog-install:" + UUID.randomUUID() + "\n")
                    .getBytes(StandardCharsets.US_ASCII);
            stagedInstallLock = Files.createTempFile(lockParent, ".install.lock-", ".tmp");
            try (FileOutputStream outputStream = new FileOutputStream(stagedInstallLock.toFile())) {
                outputStream.write(installMarker);
                outputStream.getFD().sync();
            }
            return new InstallStateTransaction(dbProperties, installLock, stagedProperties,
                    previousProperties, stagedInstallLock, installMarker);
        } catch (IOException | RuntimeException e) {
            deleteIfExists(stagedProperties, e);
            deleteIfExists(previousProperties, e);
            deleteIfExists(stagedInstallLock, e);
            throw e;
        }
    }

    private static Path stageProperties(Path parent, Map<String, String> values) throws IOException {
        Path temporaryFile = Files.createTempFile(parent, ".db.properties-", ".tmp");
        boolean ready = false;
        try {
            Properties properties = new Properties();
            properties.putAll(values);
            try (FileOutputStream outputStream = new FileOutputStream(temporaryFile.toFile())) {
                properties.store(outputStream, DB_PROPERTIES_COMMENT);
                outputStream.getFD().sync();
            }
            ready = true;
            return temporaryFile;
        } finally {
            if (!ready) {
                Files.deleteIfExists(temporaryFile);
            }
        }
    }

    private static void force(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static Path requireParent(Path target, String label) throws IOException {
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("Missing parent directory for " + label);
        }
        return parent;
    }

    private static void moveAtomically(Path source, Path target, boolean replaceExisting) throws IOException {
        try {
            if (replaceExisting) {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } else {
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    throw new FileAlreadyExistsException(target.toString());
                }
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (AtomicMoveNotSupportedException e) {
            throw new IOException("Atomic move is not supported for " + target, e);
        }
    }

    private static void deleteIfExists(Path path, Exception failure) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    static final class InstallStateTransaction implements AutoCloseable {

        private final Path dbProperties;
        private final Path installLock;
        private final boolean previousPropertiesExisted;
        private final byte[] installMarker;
        private Path stagedProperties;
        private Path previousProperties;
        private Path stagedInstallLock;
        private boolean propertiesCommitted;
        private boolean installLockCommitted;
        private boolean completed;

        private InstallStateTransaction(Path dbProperties, Path installLock, Path stagedProperties,
                                        Path previousProperties, Path stagedInstallLock, byte[] installMarker) {
            this.dbProperties = dbProperties;
            this.installLock = installLock;
            this.stagedProperties = stagedProperties;
            this.previousProperties = previousProperties;
            this.previousPropertiesExisted = previousProperties != null;
            this.stagedInstallLock = stagedInstallLock;
            this.installMarker = installMarker;
        }

        void commitForCallback() throws IOException {
            try {
                moveAtomically(stagedProperties, dbProperties, true);
                stagedProperties = null;
                propertiesCommitted = true;
                moveAtomically(stagedInstallLock, installLock, false);
                stagedInstallLock = null;
                installLockCommitted = true;
            } catch (IOException | RuntimeException e) {
                try {
                    rollback();
                } catch (IOException rollbackFailure) {
                    e.addSuppressed(rollbackFailure);
                }
                throw e;
            }
        }

        void complete() {
            completed = true;
            cleanupTemporaryFiles();
        }

        private void rollback() throws IOException {
            if (installLockCommitted) {
                if (Files.exists(installLock, LinkOption.NOFOLLOW_LINKS)) {
                    byte[] currentMarker = Files.readAllBytes(installLock);
                    if (!Arrays.equals(installMarker, currentMarker)) {
                        throw new IOException("Install lock changed during install callback; refusing to remove it");
                    }
                    Files.delete(installLock);
                }
                installLockCommitted = false;
            }
            if (propertiesCommitted) {
                if (previousPropertiesExisted) {
                    moveAtomically(previousProperties, dbProperties, true);
                    previousProperties = null;
                } else {
                    Files.deleteIfExists(dbProperties);
                }
                propertiesCommitted = false;
            }
        }

        private void cleanupTemporaryFiles() {
            cleanupTemporaryFile(stagedProperties);
            stagedProperties = null;
            cleanupTemporaryFile(previousProperties);
            previousProperties = null;
            cleanupTemporaryFile(stagedInstallLock);
            stagedInstallLock = null;
        }

        private void cleanupTemporaryFile(Path path) {
            if (path == null) {
                return;
            }
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                InstallLogUtil.logFailure(LOGGER, Level.WARNING,
                        InstallLogUtil.FailurePhase.INSTALL_STATE_CLEANUP, e);
            }
        }

        @Override
        public void close() throws IOException {
            try {
                if (!completed) {
                    rollback();
                }
            } finally {
                cleanupTemporaryFiles();
            }
        }
    }
}
