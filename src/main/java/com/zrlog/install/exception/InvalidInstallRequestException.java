package com.zrlog.install.exception;

public class InvalidInstallRequestException extends AbstractInstallException implements InstallErrorCodeProvider {

    private final String message;

    public InvalidInstallRequestException(String message) {
        this.message = message;
    }

    @Override
    public int getError() {
        return 9025;
    }

    @Override
    public String getCode() {
        return "INVALID_INSTALL_REQUEST";
    }

    @Override
    public String getMessage() {
        return message;
    }
}
