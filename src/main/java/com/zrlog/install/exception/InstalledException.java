package com.zrlog.install.exception;

import com.zrlog.install.util.InstallI18nUtil;

public class InstalledException extends AbstractInstallException implements InstallErrorCodeProvider {
    private final boolean warMode;
    private final String language;

    public InstalledException(boolean warMode, String language) {
        this.warMode = warMode;
        this.language = language;
    }

    @Override
    public int getError() {
        return 9020;
    }

    @Override
    public String getMessage() {
        return InstallI18nUtil.getInstallStringFromRes(warMode ? "installedWarTips" : "installedTips", language);
    }

    @Override
    public String getCode() {
        return "INSTALL_ALREADY_COMPLETED";
    }
}
