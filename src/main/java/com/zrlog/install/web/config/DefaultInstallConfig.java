package com.zrlog.install.web.config;

import com.hibegin.common.util.PasswordHashUtils;
import com.hibegin.http.server.api.HttpErrorHandle;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.install.business.response.InstallApiResponses;
import com.zrlog.install.business.response.LastVersionInfo;
import com.zrlog.install.exception.AbstractInstallException;
import com.zrlog.install.exception.InstallErrorCodeProvider;
import com.zrlog.install.web.InstallAction;
import com.hibegin.common.util.LoggerUtil;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DefaultInstallConfig implements InstallConfig {
    private static final Logger LOGGER = LoggerUtil.getLogger(DefaultInstallConfig.class);

    @Override
    public InstallAction getAction() {
        return new DefaultInstallAction();
    }

    @Override
    public boolean isWarMode() {
        return false;
    }

    @Override
    public String getAcceptLanguage() {
        return "zh_CN";
    }

    @Override
    public String encryptPassword(String password) {
        return PasswordHashUtils.hash(md5(password));
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());

            // 将 byte[] 转换为十六进制字符串
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }

    @Override
    public String defaultTemplatePath() {
        return "/include/templates/hexo-theme-fluid";
    }

    @Override
    public String getZrLogSqlVersion() {
        return "25";
    }

    @Override
    public File getDbPropertiesFile() {
        return PathUtil.getConfFile("db.properties");
    }

    @Override
    public LastVersionInfo getLastVersionInfo() {
        LastVersionInfo lastVersionInfo = new LastVersionInfo();
        lastVersionInfo.setLatestVersion(false);
        lastVersionInfo.setNewVersion("3.2.0");
        lastVersionInfo.setDownloadUrl("https://dl.zrlog.com/release/zrlog.zip");
        lastVersionInfo.setChangeLog("### Change Log content\n1. test1\n2. test2\n3. test3");
        return lastVersionInfo;
    }

    @Override
    public String getBuildVersion() {
        return "1.0.0-SNAPSHOT";
    }

    @Override
    public String getJdbcUrlQueryStr(String dbType, Map<String, String[]> paramMap) {
        if (Objects.equals(dbType, "mysql")) {
            return "characterEncoding=utf8&allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=GMT";
        }
        return "";
    }

    @Override
    public HttpErrorHandle getErrorHandler() {
        return (request, response, e) -> {
            if (e instanceof AbstractInstallException) {
                AbstractInstallException ee = (AbstractInstallException) e;
                String code = null;
                if (ee instanceof InstallErrorCodeProvider) {
                    code = ((InstallErrorCodeProvider) ee).getCode();
                }
                response.renderJson(InstallApiResponses.error(ee.getError(), ee.getMessage(), code));
            } else {
                LOGGER.log(Level.SEVERE, "Install request failed", e);
                response.renderJson(InstallApiResponses.error(9999, e.getMessage(), null));
            }
        };
    }

    @Override
    public boolean isAskConfig() {
        return true;
    }

    @Override
    public boolean isMissingConfig() {
        return true;
    }
}
