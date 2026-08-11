package com.zrlog.install.util;

import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.IOUtil;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.template.BasicTemplateRender;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

public class InstallSuccessContentUtils {

    private static Map<String, Object> getInstallInfo(File dbProperties, ServerConfig serverConfig) throws IOException {
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
            data.setDbProperties(Arrays.stream(IOUtil.getStringInputStream(new FileInputStream(dbProperties)).split("\n"))
                    .filter(e -> !e.startsWith("#"))
                    .collect(Collectors.joining("<br/>")));
            return data.toTemplateMap();
        }
    }

    private static String sqliteDatabasePath(String jdbcUrl) {
        String pathAndQuery = jdbcUrl.substring("jdbc:sqlite:".length());
        int queryIndex = pathAndQuery.indexOf('?');
        return queryIndex < 0 ? pathAndQuery : pathAndQuery.substring(0, queryIndex);
    }

    private static String getMdFilePath() {
        if (EnvKit.isFaaSMode()) {
            return "/i18n/installed-faas/zh_CN.md";
        }
        return "/i18n/installed-docker/zh_CN.md";
    }

    public static String getContent(File dbProperties, boolean askConfig, ServerConfig serverConfig) {
        if (askConfig) {
            BasicTemplateRender basicTemplateRender;
            try {
                basicTemplateRender = new BasicTemplateRender(getInstallInfo(dbProperties, serverConfig), InstallSuccessContentUtils.class);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return basicTemplateRender.render(InstallSuccessContentUtils.class.getResourceAsStream(getMdFilePath()));
        }
        return "";
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
