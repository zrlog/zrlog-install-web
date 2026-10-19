package com.zrlog.install.exception;

import com.zrlog.install.business.type.TestConnectDbResult;
import com.zrlog.install.util.InstallI18nUtil;

public class InstallException extends AbstractInstallException implements InstallErrorCodeProvider {

    private final TestConnectDbResult result;
    private final String language;

    public InstallException(TestConnectDbResult result, String language) {
        this.language = language;
        this.result = result;
    }

    @Override
    public int getError() {
        return 9000;
    }

    @Override
    public String getMessage() {
        return "[Error-" + result + "] - " + InstallI18nUtil.getInstallStringFromRes("connectDbError_" + result.getError(), language);
    }

    @Override
    public String getCode() {
        return result.name();
    }
}
