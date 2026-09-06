package com.zrlog.install.business.response;

public class InstallUpgradeResult {

    private final boolean finish;
    private final String message;
    private final String code;

    public InstallUpgradeResult(boolean finish, String message) {
        this(finish, message, null);
    }

    public InstallUpgradeResult(boolean finish, String message, String code) {
        this.finish = finish;
        this.message = message;
        this.code = code;
    }

    public boolean isFinish() {
        return finish;
    }

    public String getMessage() {
        return message;
    }

    public String getCode() {
        return code;
    }
}
