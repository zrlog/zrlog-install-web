package com.zrlog.install.business.service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class LocalSqliteReservationProcess {

    private LocalSqliteReservationProcess() {
    }

    public static void main(String[] args) throws Exception {
        FakeInstallConfig config = new FakeInstallConfig(new File(args[0]), new File(args[1]));
        Path readyFile = Path.of(args[2]);
        Path startFile = Path.of(args[3]);
        Path resultFile = Path.of(args[4]);
        Files.createFile(readyFile);
        while (!Files.exists(startFile)) {
            Thread.sleep(10);
        }
        String result;
        try (LocalSqliteSupport.DatabaseReservation reservation =
                     LocalSqliteSupport.reserveDatabaseFile(config)) {
            reservation.preserve();
            result = "CREATED";
        } catch (FileAlreadyExistsException e) {
            result = "EXISTS";
        }
        Files.writeString(resultFile, result, StandardCharsets.US_ASCII, StandardOpenOption.CREATE_NEW);
    }
}
