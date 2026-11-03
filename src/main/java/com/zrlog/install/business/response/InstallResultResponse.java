package com.zrlog.install.business.response;

import com.zrlog.install.business.vo.InstallSuccessData;

public class InstallResultResponse extends InstallApiStandardResponse {

    private InstallSuccessData data;

    public InstallResultResponse() {}

    public InstallResultResponse(InstallSuccessData data) {
        this.data = data;
    }

    public InstallSuccessData getData() {
        return data;
    }

    public void setData(InstallSuccessData data) {
        this.data = data;
    }
}
