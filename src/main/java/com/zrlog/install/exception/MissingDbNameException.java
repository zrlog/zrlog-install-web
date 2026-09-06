package com.zrlog.install.exception;

import com.zrlog.install.util.InstallI18nUtil;

public class MissingDbNameException extends AbstractInstallException {
    @Override
    public int getError() {
        return 9023;
    }

    @Override
    public String getMessage() {
        return InstallI18nUtil.getInstallStringFromRes("missingDbName");
    }
}
