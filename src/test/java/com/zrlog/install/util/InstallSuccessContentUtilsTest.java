package com.zrlog.install.util;

import com.hibegin.http.server.config.ServerConfig;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InstallSuccessContentUtilsTest {

    @Test
    public void shouldReturnEmptyContentWhenConfigIsNotRequested() throws Exception {
        File dbProperties = Files.createTempFile("zrlog-db", ".properties").toFile();

        assertEquals("", InstallSuccessContentUtils.getContent(dbProperties, false, new ServerConfig()));
    }

    @Test
    public void shouldRenderDockerInstallContentFromDbProperties() throws Exception {
        File dbProperties = Files.createTempFile("zrlog-db", ".properties").toFile();
        Files.write(dbProperties.toPath(), (
                "# ignored comment\n" +
                        "user=zrlog\n" +
                        "password=secret\n" +
                        "jdbcUrl=jdbc:mysql://localhost:3307/zrlog?characterEncoding=utf8\n")
                .getBytes(StandardCharsets.UTF_8));

        String content = InstallSuccessContentUtils.getContent(dbProperties, true, new ServerConfig());

        assertTrue(content.contains("db.password=secret"));
        assertTrue(content.contains("db.host=localhost"));
        assertTrue(content.contains("db.port=3307"));
        assertTrue(content.contains("db.username=zrlog"));
        assertTrue(content.contains("db.database=zrlog"));
        assertTrue(content.contains("db.type=mysql"));
    }

    @Test
    public void shouldRenderSqliteDatabasePathFromDbProperties() throws Exception {
        File dbProperties = Files.createTempFile("zrlog-sqlite-db", ".properties").toFile();
        Files.write(dbProperties.toPath(), (
                "user=\n" +
                        "password=\n" +
                        "dbType=sqlite\n" +
                        "jdbcUrl=jdbc:sqlite:/opt/zrlog/conf/zrlog.db?journal_mode=WAL&busy_timeout=10000\n")
                .getBytes(StandardCharsets.UTF_8));

        String content = InstallSuccessContentUtils.getContent(dbProperties, true, new ServerConfig());

        assertTrue(content.contains("db.database=/opt/zrlog/conf/zrlog.db"));
        assertTrue(content.contains("db.type=sqlite"));
    }
}
