package com.zrlog.install.exception;

import com.zrlog.install.util.InstallI18nUtil;
public class MissingDbPortException extends AbstractInstallException {
    @Override
    public int getError() {
        return 9022;
    }

    @Override
    public String getMessage() {
        return InstallI18nUtil.getInstallStringFromRes("missingDbPort");
    }
}
