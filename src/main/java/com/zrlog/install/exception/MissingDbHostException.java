package com.zrlog.install.exception;

import com.zrlog.install.util.InstallI18nUtil;

public class MissingDbHostException extends AbstractInstallException {

    private final String language;

    public MissingDbHostException(String language) {
        this.language = language;
    }

    @Override
    public int getError() {
        return 9021;
    }

    @Override
    public String getMessage() {
        return InstallI18nUtil.getInstallStringFromRes("missingDbHost", language);
    }
}
