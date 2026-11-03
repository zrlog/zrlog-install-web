package com.zrlog.install.business.response;

public class InstallResourceResponse extends InstallApiStandardResponse {

    private Object data;

    public InstallResourceResponse() {}

    public InstallResourceResponse(Object data) {
        this.data = data;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}
