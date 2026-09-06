package com.zrlog.install.util;

import com.hibegin.common.util.EnvKit;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.template.BasicTemplateRender;
import com.zrlog.install.web.InstallConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public class InstallSuccessContentUtils {

    private static final String DEFAULT_LANGUAGE = "zh_CN";
    private static final String ENGLISH_LANGUAGE = "en_US";

    private static Map<String, Object> getInstallInfo(File dbProperties) throws IOException {
        Properties dataSourceProperties = new Properties();
        try (FileInputStream fileInputStream = new FileInputStream(dbProperties)) {
            dataSourceProperties.load(fileInputStream);
            String jdbcUrl = dataSourceProperties.getProperty("jdbcUrl");
            InstallSuccessTemplateData data = new InstallSuccessTemplateData();
            data.setDbUserName(dataSourceProperties.getProperty("user"));
            data.setDbPassword(dataSourceProperties.getProperty("password"));
            if (jdbcUrl.startsWith("jdbc:sqlite:")) {
                data.setDbHost("");
                data.setDbPort("");
                data.setDbName(sqliteDatabasePath(jdbcUrl));
                data.setDbType("sqlite");
            } else {
                URI uri = URI.create(jdbcUrl.replaceFirst("jdbc:", ""));
                data.setDbHost(uri.getHost());
                data.setDbPort(String.valueOf(uri.getPort()));
                data.setDbName(uri.getPath().substring(1));
                data.setDbType(uri.getScheme());
            }
            data.setDbProperties(Files.readString(dbProperties.toPath(), StandardCharsets.UTF_8));
            return data.toTemplateMap();
        }
    }

    private static String sqliteDatabasePath(String jdbcUrl) {
        String pathAndQuery = jdbcUrl.substring("jdbc:sqlite:".length());
        int queryIndex = pathAndQuery.indexOf('?');
        return queryIndex < 0 ? pathAndQuery : pathAndQuery.substring(0, queryIndex);
    }

    static String getMdFilePath(boolean faasMode, String language) {
        String directory = faasMode ? "/i18n/installed-faas/" : "/i18n/installed-docker/";
        String candidate = directory + normalizeLanguage(language) + ".md";
        if (InstallSuccessContentUtils.class.getResource(candidate) != null) {
            return candidate;
        }
        return directory + DEFAULT_LANGUAGE + ".md";
    }

    private static String normalizeLanguage(String language) {
        if (ENGLISH_LANGUAGE.equals(language)) {
            return ENGLISH_LANGUAGE;
        }
        return DEFAULT_LANGUAGE;
    }

    public static String getContent(File dbProperties, boolean askConfig, ServerConfig serverConfig) {
        String language = InstallConstants.installConfig == null
                ? DEFAULT_LANGUAGE : InstallConstants.installConfig.getAcceptLanguage();
        return getContent(dbProperties, askConfig, EnvKit.isFaaSMode(), language);
    }

    static String getContent(File dbProperties, boolean askConfig, boolean faasMode, String language) {
        if (!askConfig) {
            return "";
        }
        try {
            BasicTemplateRender templateRender = new BasicTemplateRender(
                    getInstallInfo(dbProperties), InstallSuccessContentUtils.class);
            String templatePath = getMdFilePath(faasMode, language);
            try (InputStream inputStream = InstallSuccessContentUtils.class.getResourceAsStream(templatePath)) {
                if (inputStream == null) {
                    throw new IOException("Install success template is unavailable");
                }
                return templateRender.render(inputStream);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class InstallSuccessTemplateData {

        private String dbUserName;
        private String dbPassword;
        private String dbHost;
        private String dbPort;
        private String dbName;
        private String dbType;
        private String dbProperties;

        Map<String, Object> toTemplateMap() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("dbUserName", dbUserName);
            data.put("dbPassword", dbPassword);
            data.put("dbHost", dbHost);
            data.put("dbPort", dbPort);
            data.put("dbName", dbName);
            data.put("dbType", dbType);
            data.put("dbProperties", dbProperties);
            return data;
        }

        public void setDbUserName(String dbUserName) {
            this.dbUserName = dbUserName;
        }

        public void setDbPassword(String dbPassword) {
            this.dbPassword = dbPassword;
        }

        public void setDbHost(String dbHost) {
            this.dbHost = dbHost;
        }

        public void setDbPort(String dbPort) {
            this.dbPort = dbPort;
        }

        public void setDbName(String dbName) {
            this.dbName = dbName;
        }

        public void setDbType(String dbType) {
            this.dbType = dbType;
        }

        public void setDbProperties(String dbProperties) {
            this.dbProperties = dbProperties;
        }
    }
}
