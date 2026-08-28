package com.zrlog.install.business.service;

import com.zrlog.install.business.vo.InstallDatabaseConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;

final class InstallRecoveryStore {

    private static final String FILE_NAME = ".install-recovery.properties";
    private static final String VERSION_KEY = "recoveryVersion";
    private static final String VERSION = "1";
    private static final long MAX_FILE_SIZE = 64 * 1024;
    private static final Pattern DATABASE_TYPE = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    static File recoveryFile(File installLockFile) throws IOException {
        Path installLock = installLockFile.toPath().toAbsolutePath().normalize();
        Path parent = installLock.getParent();
        if (parent == null) {
            throw new IOException("Missing parent directory for install recovery state");
        }
        return parent.resolve(FILE_NAME).toFile();
    }

    boolean isAvailable(File installLockFile) {
        try {
            return load(installLockFile).isPresent();
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    Optional<InstallDatabaseConfig> load(File installLockFile) throws IOException {
        Path recoveryFile = recoveryFile(installLockFile).toPath();
        if (!Files.isRegularFile(recoveryFile, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        long fileSize = Files.size(recoveryFile);
        if (fileSize == 0 || fileSize > MAX_FILE_SIZE) {
            return Optional.empty();
        }
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(recoveryFile, LinkOption.NOFOLLOW_LINKS)) {
            try {
                properties.load(inputStream);
            } catch (IllegalArgumentException malformedProperties) {
                return Optional.empty();
            }
        }
        if (!VERSION.equals(properties.getProperty(VERSION_KEY))) {
            return Optional.empty();
        }
        properties.remove(VERSION_KEY);
        InstallDatabaseConfig databaseConfig = InstallDatabaseConfig.from(toStringMap(properties));
        if (!isValid(databaseConfig)) {
            return Optional.empty();
        }
        return Optional.of(databaseConfig);
    }

    void save(File installLockFile, InstallDatabaseConfig databaseConfig) throws IOException {
        Path recoveryFile = recoveryFile(installLockFile).toPath();
        Path parent = recoveryFile.getParent();
        Files.createDirectories(parent);
        Path temporaryFile = Files.createTempFile(parent, ".install-recovery-", ".tmp");
        boolean committed = false;
        try {
            restrictPermissions(temporaryFile);
            Properties properties = new Properties();
            properties.putAll(databaseConfig.toMap());
            properties.setProperty(VERSION_KEY, VERSION);
            try (FileOutputStream outputStream = new FileOutputStream(temporaryFile.toFile())) {
                properties.store(outputStream, "ZrLog installation recovery state");
                outputStream.getFD().sync();
            }
            try {
                Files.move(temporaryFile, recoveryFile,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                throw new IOException("Atomic move is not supported for install recovery state", e);
            }
            committed = true;
        } finally {
            if (!committed) {
                Files.deleteIfExists(temporaryFile);
            }
        }
    }

    void clear(File installLockFile) throws IOException {
        Files.deleteIfExists(recoveryFile(installLockFile).toPath());
    }

    private static Map<String, String> toStringMap(Properties properties) {
        Map<String, String> values = new java.util.LinkedHashMap<>();
        for (String name : properties.stringPropertyNames()) {
            values.put(name, properties.getProperty(name));
        }
        return values;
    }

    private static boolean isValid(InstallDatabaseConfig databaseConfig) {
        String dbType = databaseConfig.getDbType();
        String jdbcUrl = databaseConfig.getJdbcUrl();
        if (dbType == null || jdbcUrl == null || !DATABASE_TYPE.matcher(dbType).matches()) {
            return false;
        }
        String normalizedType = dbType.toLowerCase(Locale.ROOT);
        if (!jdbcUrl.toLowerCase(Locale.ROOT).startsWith("jdbc:" + normalizedType + ":")) {
            return false;
        }
        return "webapi".equals(normalizedType)
                || (databaseConfig.getDriverClass() != null
                && !databaseConfig.getDriverClass().trim().isEmpty());
    }

    private static void restrictPermissions(Path file) throws IOException {
        try {
            Files.setPosixFilePermissions(file, EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // createTempFile still uses the platform's private temporary-file defaults.
        }
    }
}
