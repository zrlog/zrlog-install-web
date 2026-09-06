package com.zrlog.install.exception;

import com.zrlog.install.util.InstallI18nUtil;

public class InstallRecoveryUnavailableException extends AbstractInstallException
        implements InstallErrorCodeProvider {

    @Override
    public int getError() {
        return 9026;
    }

    @Override
    public String getCode() {
        return "INSTALL_RECOVERY_UNAVAILABLE";
    }

    @Override
    public String getMessage() {
        String message = InstallI18nUtil.getInstallStringFromRes("installRecoveryUnavailable");
        return message.isEmpty() ? "Installation recovery is unavailable" : message;
    }
}
