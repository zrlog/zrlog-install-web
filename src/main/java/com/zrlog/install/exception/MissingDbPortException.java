package com.zrlog.install.exception;

import com.zrlog.install.util.InstallI18nUtil;
public class MissingDbPortException extends AbstractInstallException {

    private final String language;

    public MissingDbPortException(String language) {
        this.language = language;
    }

    @Override
    public int getError() {
        return 9022;
    }

    @Override
    public String getMessage() {
        return InstallI18nUtil.getInstallStringFromRes("missingDbPort", language);
    }
}
