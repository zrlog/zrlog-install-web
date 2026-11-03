package com.zrlog.install.business.response;

public class InstallProgressEvent {

    private String code;
    private String status;
    private String detail;

    public InstallProgressEvent() {}

    public InstallProgressEvent(String code, String status) {
        this.code = code;
        this.status = status;
    }

    public InstallProgressEvent(String code, String status, String detail) {
        this.code = code;
        this.status = status;
        this.detail = detail;
    }

    public static InstallProgressEvent running(String code) {
        return new InstallProgressEvent(code, "running");
    }

    public static InstallProgressEvent complete(String code) {
        return new InstallProgressEvent(code, "complete");
    }

    public static InstallProgressEvent error(String code, String detail) {
        return new InstallProgressEvent(code, "error", detail);
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
