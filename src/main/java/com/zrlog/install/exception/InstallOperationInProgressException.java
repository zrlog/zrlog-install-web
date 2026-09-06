package com.zrlog.install.exception;

public class InstallOperationInProgressException extends AbstractInstallException
        implements InstallErrorCodeProvider {

    private final String activeOperation;

    public InstallOperationInProgressException(String activeOperation) {
        this.activeOperation = "UPGRADE".equalsIgnoreCase(activeOperation) ? "UPGRADE" : "INSTALL";
    }

    @Override
    public int getError() {
        return 9024;
    }

    @Override
    public String getCode() {
        return activeOperation + "_IN_PROGRESS";
    }

    @Override
    public String getMessage() {
        return "UPGRADE".equals(activeOperation)
                ? "An upgrade is already running" : "An installation is already running";
    }
}
