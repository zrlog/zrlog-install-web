package com.zrlog.install.exception;

import com.zrlog.install.util.InstallI18nUtil;

public class InstallRecoveryUnavailableException extends AbstractInstallException
        implements InstallErrorCodeProvider {

    private final String language;

    public InstallRecoveryUnavailableException(String language) {
        this.language = language;
    }

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
        String message = InstallI18nUtil.getInstallStringFromRes("installRecoveryUnavailable", language);
        return message.isEmpty() ? "Installation recovery is unavailable" : message;
    }
}
