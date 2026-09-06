package com.zrlog.install.business.service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public final class InstallOperationLockProcess {

    private InstallOperationLockProcess() {
    }

    public static void main(String[] args) throws Exception {
        File installLockFile = new File(args[0]);
        Path readyFile = Path.of(args[1]);
        Path releaseFile = Path.of(args[2]);
        try (InstallOperationLock operationLock = InstallOperationLock.tryAcquire(installLockFile)) {
            if (operationLock == null) {
                System.exit(2);
            }
            Files.createFile(readyFile);
            while (!Files.exists(releaseFile)) {
                Thread.sleep(10);
            }
        }
    }
}
