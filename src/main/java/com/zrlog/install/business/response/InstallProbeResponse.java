package com.zrlog.install.business.response;

public class InstallProbeResponse extends InstallApiStandardResponse {

    private InstallProbeData data;

    public InstallProbeResponse() {}

    public InstallProbeResponse(InstallProbeData data) {
        this.data = data;
    }

    public InstallProbeData getData() {
        return data;
    }

    public void setData(InstallProbeData data) {
        this.data = data;
    }
}
