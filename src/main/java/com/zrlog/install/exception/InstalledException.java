package com.zrlog.install.exception;

import com.zrlog.install.util.InstallI18nUtil;
import com.zrlog.install.web.InstallConstants;

public class InstalledException extends AbstractInstallException implements InstallErrorCodeProvider {
    @Override
    public int getError() {
        return 9020;
    }

    @Override
    public String getMessage() {
        return InstallI18nUtil.getInstallStringFromRes(InstallConstants.installConfig.isWarMode() ? "installedWarTips" : "installedTips");
    }

    @Override
    public String getCode() {
        return "INSTALL_ALREADY_COMPLETED";
    }
}
