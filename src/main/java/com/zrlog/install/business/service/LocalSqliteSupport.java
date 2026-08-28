package com.zrlog.install.business.service;

import com.zrlog.install.business.vo.InstallDatabaseConfig;
import com.zrlog.install.web.config.InstallConfig;

import java.io.File;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class LocalSqliteSupport {

    private static final String PROBE_PREFIX = ".zrlog-sqlite-probe-";
    private static final Set<OpenOption> EXCLUSIVE_CREATE_OPTIONS = Set.of(
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
    private static final Set<PosixFilePermission> OWNER_ONLY_PERMISSIONS = EnumSet.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private LocalSqliteSupport() {
    }

    public static boolean isAvailable(InstallConfig installConfig) {
        return isSupportedRuntimeMode(InstallProbeService.getRuntimeMode(installConfig))
                && isDriverAvailable()
                && isStorageAvailable(installConfig);
    }

    static boolean isSupportedRuntimeMode(String runtimeMode) {
        return Objects.equals(runtimeMode, "zip") || Objects.equals(runtimeMode, "native");
    }

    private static boolean isDriverAvailable() {
        try {
            Class.forName("org.sqlite.JDBC", false, LocalSqliteSupport.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    public static void ensureDatabaseDirectory(InstallConfig installConfig) throws IOException {
        File configParent = getConfigParent(installConfig);
        Files.createDirectories(configParent.toPath());
        if (!Files.isDirectory(configParent.toPath()) || !Files.isWritable(configParent.toPath())) {
            throw new IOException("Local SQLite directory is not writable: " + configParent.getAbsolutePath());
        }
    }

    private static boolean isStorageAvailable(InstallConfig installConfig) {
        Path probeFile = null;
        try {
            ensureDatabaseDirectory(installConfig);
            Path configFile = installConfig.getDbPropertiesFile().toPath().toAbsolutePath().normalize();
            Path databaseFile = getDatabaseFile(installConfig).toPath();
            if (!isWritableFileTarget(configFile)) {
                return false;
            }
            probeFile = Files.createTempFile(databaseFile.getParent(), PROBE_PREFIX, ".tmp");
            return Files.isRegularFile(probeFile) && Files.isWritable(probeFile);
        } catch (IOException | RuntimeException e) {
            return false;
        } finally {
            if (probeFile != null) {
                try {
                    Files.deleteIfExists(probeFile);
                } catch (IOException ignored) {
                    // A failed cleanup also makes the next preflight fail with a stable generic error.
                }
            }
        }
    }

    private static boolean isWritableFileTarget(Path target) {
        return !Files.exists(target) || Files.isRegularFile(target) && Files.isWritable(target);
    }

    public static InstallDatabaseConfig createDatabaseConfig(InstallConfig installConfig) {
        return createDatabaseConfig(getDatabaseFile(installConfig).toPath());
    }

    static boolean databaseTargetExists(InstallConfig installConfig) {
        return Files.exists(getDatabaseFile(installConfig).toPath(), LinkOption.NOFOLLOW_LINKS);
    }

    static ConnectionProbe createConnectionProbe(InstallConfig installConfig) throws IOException {
        ensureDatabaseDirectory(installConfig);
        Path databaseDirectory = getDatabaseFile(installConfig).toPath().getParent();
        Path probeFile = Files.createTempFile(databaseDirectory, PROBE_PREFIX, ".db");
        return new ConnectionProbe(probeFile, createDatabaseConfig(probeFile));
    }

    static DatabaseReservation reserveDatabaseFile(InstallConfig installConfig) throws IOException {
        ensureDatabaseDirectory(installConfig);
        Path databaseFile = getDatabaseFile(installConfig).toPath();
        FileStore fileStore = Files.getFileStore(databaseFile.getParent());
        Object fileKey = null;
        try {
            if (fileStore.supportsFileAttributeView(PosixFileAttributeView.class)) {
                try (SeekableByteChannel ignored = Files.newByteChannel(databaseFile, EXCLUSIVE_CREATE_OPTIONS,
                        PosixFilePermissions.asFileAttribute(OWNER_ONLY_PERMISSIONS))) {
                    fileKey = readFileKey(databaseFile);
                }
            } else {
                try (SeekableByteChannel ignored = Files.newByteChannel(databaseFile, EXCLUSIVE_CREATE_OPTIONS)) {
                    fileKey = readFileKey(databaseFile);
                    restrictNonPosixPermissions(databaseFile);
                }
            }
            return new DatabaseReservation(databaseFile, fileKey);
        } catch (IOException | RuntimeException e) {
            deleteIfOwned(databaseFile, fileKey, e);
            throw e;
        }
    }

    private static Object readFileKey(Path databaseFile) throws IOException {
        return Files.readAttributes(databaseFile, BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS).fileKey();
    }

    private static void deleteIfOwned(Path databaseFile, Object expectedFileKey, Throwable failure) {
        if (expectedFileKey == null) {
            return;
        }
        try {
            BasicFileAttributes attributes = Files.readAttributes(databaseFile, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (isSameReservationTarget(expectedFileKey, attributes.fileKey(), attributes.isRegularFile())) {
                Files.delete(databaseFile);
            }
        } catch (java.nio.file.NoSuchFileException ignored) {
            // The reserved target has already gone; never delete a replacement by path.
        } catch (IOException | RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    static boolean isSameReservationTarget(Object expectedFileKey, Object currentFileKey,
                                           boolean regularFile) {
        return expectedFileKey != null && regularFile && Objects.equals(expectedFileKey, currentFileKey);
    }

    private static void restrictNonPosixPermissions(Path databaseFile) throws IOException {
        File file = databaseFile.toFile();
        boolean restricted = file.setReadable(false, false);
        restricted = file.setWritable(false, false) && restricted;
        restricted = file.setExecutable(false, false) && restricted;
        restricted = file.setReadable(true, true) && restricted;
        restricted = file.setWritable(true, true) && restricted;
        if (!restricted) {
            throw new IOException("Can not restrict local SQLite database permissions");
        }
    }

    private static InstallDatabaseConfig createDatabaseConfig(Path databaseFile) {
        InstallDatabaseConfig dbConn = new InstallDatabaseConfig();
        dbConn.setUser("");
        dbConn.setPassword("");
        dbConn.setDbType("sqlite");
        dbConn.setDbName("zrlog");
        dbConn.setDriverClass("org.sqlite.JDBC");
        dbConn.setJdbcUrl("jdbc:sqlite:" + databaseFile.toAbsolutePath().normalize()
                + "?journal_mode=WAL&busy_timeout=10000&foreign_keys=on&synchronous=NORMAL"
                + "&date_class=TEXT&date_string_format=yyyy-MM-dd HH:mm:ss");
        return dbConn;
    }

    static File getDatabaseFile(InstallConfig installConfig) {
        return new File(getConfigParent(installConfig), "zrlog.db")
                .toPath().toAbsolutePath().normalize().toFile();
    }

    private static File getConfigParent(InstallConfig installConfig) {
        File configParent = installConfig.getDbPropertiesFile().getAbsoluteFile().getParentFile();
        if (configParent == null) {
            throw new IllegalStateException("Missing database configuration directory");
        }
        return configParent;
    }

    static final class ConnectionProbe implements AutoCloseable {

        private final Path databaseFile;
        private final InstallDatabaseConfig databaseConfig;

        private ConnectionProbe(Path databaseFile, InstallDatabaseConfig databaseConfig) {
            this.databaseFile = databaseFile;
            this.databaseConfig = databaseConfig;
        }

        InstallDatabaseConfig getDatabaseConfig() {
            return databaseConfig;
        }

        @Override
        public void close() throws IOException {
            IOException failure = null;
            for (Path path : new Path[]{
                    sidecar("-shm"),
                    sidecar("-wal"),
                    sidecar("-journal"),
                    databaseFile}) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }

        private Path sidecar(String suffix) {
            return databaseFile.resolveSibling(databaseFile.getFileName() + suffix);
        }
    }

    static final class DatabaseReservation implements AutoCloseable {

        private final Path databaseFile;
        private final Object fileKey;
        private boolean preserved;

        private DatabaseReservation(Path databaseFile, Object fileKey) {
            this.databaseFile = databaseFile;
            this.fileKey = fileKey;
        }

        void preserve() {
            preserved = true;
        }

        @Override
        public void close() throws IOException {
            if (preserved || !Files.exists(databaseFile, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            BasicFileAttributes attributes;
            try {
                attributes = Files.readAttributes(databaseFile, BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
            } catch (java.nio.file.NoSuchFileException ignored) {
                return;
            }
            if (!isSameReservationTarget(fileKey, attributes.fileKey(), attributes.isRegularFile())) {
                return;
            }
            Files.delete(databaseFile);
        }
    }
}
