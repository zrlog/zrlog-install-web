package com.zrlog.install.util;

import com.hibegin.http.server.config.ServerConfig;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

    @Test
    public void shouldSelectLocalizedFaaSTemplatesAndFallbackSafely() {
        assertEquals("/i18n/installed-faas/zh_CN.md",
                InstallSuccessContentUtils.getMdFilePath(true, "zh_CN"));
        assertEquals("/i18n/installed-faas/en_US.md",
                InstallSuccessContentUtils.getMdFilePath(true, "en_US"));
        assertEquals("/i18n/installed-faas/zh_CN.md",
                InstallSuccessContentUtils.getMdFilePath(true, "unsupported"));
        assertEquals("/i18n/installed-docker/zh_CN.md",
                InstallSuccessContentUtils.getMdFilePath(false, "en_US"));
    }

    @Test
    public void shouldRenderCompleteChineseFaaSConfigurationAsMarkdown() throws Exception {
        File dbProperties = faasDbProperties();

        String content = InstallSuccessContentUtils.getContent(dbProperties, true, true, "zh_CN");

        assertTrue(content.contains("还差一步"));
        assertTrue(content.contains("`DB_PROPERTIES`"));
        assertTrue(content.contains("# generated configuration\nuser=zrlog\npassword=secret\n"));
        assertTrue(content.contains("jdbcUrl=jdbc:mysql://database.internal:3306/zrlog\n"));
        assertTrue(content.contains("Asia/Shanghai"));
        assertFalse(content.contains("Asia/Chongqing"));
        assertFalse(content.contains("<style"));
        assertFalse(content.contains("style="));
        assertFalse(content.contains("<br"));
    }

    @Test
    public void shouldRenderEnglishFaaSConfiguration() throws Exception {
        String content = InstallSuccessContentUtils.getContent(faasDbProperties(), true, true, "en_US");

        assertTrue(content.contains("One more step"));
        assertTrue(content.contains("complete value below"));
        assertTrue(content.contains("`DB_PROPERTIES`"));
        assertTrue(content.contains("Asia/Shanghai"));
        assertFalse(content.contains("还差一步"));
    }

    private static File faasDbProperties() throws Exception {
        File dbProperties = Files.createTempFile("zrlog-faas-db", ".properties").toFile();
        Files.write(dbProperties.toPath(), (
                "# generated configuration\n" +
                        "user=zrlog\n" +
                        "password=secret\n" +
                        "jdbcUrl=jdbc:mysql://database.internal:3306/zrlog\n")
                .getBytes(StandardCharsets.UTF_8));
        return dbProperties;
    }
}
