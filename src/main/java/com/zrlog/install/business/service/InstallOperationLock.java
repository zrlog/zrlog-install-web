package com.zrlog.install.business.service;

import com.hibegin.common.util.LoggerUtil;
import com.zrlog.install.util.InstallLogUtil;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class InstallOperationLock implements AutoCloseable {

    private static final Logger LOGGER = LoggerUtil.getLogger(InstallOperationLock.class);
    private static final String OPERATION_LOCK_FILE_NAME = ".install-operation.lock";

    private final FileChannel channel;
    private final FileLock lock;

    private InstallOperationLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    public static InstallOperationLock tryAcquire(File installLockFile) throws IOException {
        Path operationLockFile = operationLockFile(installLockFile);
        Files.createDirectories(operationLockFile.getParent());
        FileChannel channel = FileChannel.open(operationLockFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                return null;
            }
            return new InstallOperationLock(channel, lock);
        } catch (OverlappingFileLockException e) {
            channel.close();
            return null;
        } catch (IOException | RuntimeException e) {
            channel.close();
            throw e;
        }
    }

    static Path operationLockFile(File installLockFile) throws IOException {
        Path installLockPath = installLockFile.toPath().toAbsolutePath().normalize();
        Path parent = installLockPath.getParent();
        if (parent == null) {
            throw new IOException("Missing parent directory for install operation lock");
        }
        return parent.resolve(OPERATION_LOCK_FILE_NAME);
    }

    @Override
    public void close() {
        try {
            lock.release();
        } catch (IOException e) {
            InstallLogUtil.logFailure(LOGGER, Level.WARNING,
                    InstallLogUtil.FailurePhase.OPERATION_LOCK_RELEASE, e);
        } finally {
            try {
                channel.close();
            } catch (IOException e) {
                InstallLogUtil.logFailure(LOGGER, Level.WARNING,
                        InstallLogUtil.FailurePhase.OPERATION_LOCK_CHANNEL_CLOSE, e);
            }
        }
        // Keep the file: deleting it can let contenders lock different inodes during handoff.
    }
}
