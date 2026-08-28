package com.zrlog.install.exception;

import com.zrlog.install.util.InstallI18nUtil;

public class MissingDbHostException extends AbstractInstallException {
    @Override
    public int getError() {
        return 9021;
    }

    @Override
    public String getMessage() {
        return InstallI18nUtil.getInstallStringFromRes("missingDbHost");
    }
}
