package com.zrlog.install.exception;

import com.zrlog.install.util.InstallI18nUtil;

public class MissingDbNameException extends AbstractInstallException {

    private final String language;

    public MissingDbNameException(String language) {
        this.language = language;
    }

    @Override
    public int getError() {
        return 9023;
    }

    @Override
    public String getMessage() {
        return InstallI18nUtil.getInstallStringFromRes("missingDbName", language);
    }
}
