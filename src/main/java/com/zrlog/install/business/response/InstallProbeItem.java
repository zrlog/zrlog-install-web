package com.zrlog.install.business.response;

public class InstallProbeItem {

    private String code;
    private String category;
    private String status;
    private String value;

    public InstallProbeItem() {
    }

    public InstallProbeItem(String code, String category, String status, String value) {
        this.code = code;
        this.category = category;
        this.status = status;
        this.value = value;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

}
