package com.zrlog.install.business.response;

public class InstallUpgradeResult {

    private final boolean finish;
    private final String message;

    public InstallUpgradeResult(boolean finish, String message) {
        this.finish = finish;
        this.message = message;
    }

    public boolean isFinish() {
        return finish;
    }

    public String getMessage() {
        return message;
    }
}
