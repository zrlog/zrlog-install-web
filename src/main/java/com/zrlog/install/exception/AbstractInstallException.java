package com.zrlog.install.exception;

public abstract class AbstractInstallException extends RuntimeException {

    public abstract int getError();
}
