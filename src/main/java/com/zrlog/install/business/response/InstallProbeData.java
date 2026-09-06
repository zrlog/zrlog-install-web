package com.zrlog.install.business.response;

import java.util.ArrayList;
import java.util.List;

public class InstallProbeData {

    private String status;
    private String runtimeMode;
    private String charset;
    private List<InstallProbeItem> items = new ArrayList<>();

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRuntimeMode() {
        return runtimeMode;
    }

    public void setRuntimeMode(String runtimeMode) {
        this.runtimeMode = runtimeMode;
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public List<InstallProbeItem> getItems() {
        return items;
    }

    public void setItems(List<InstallProbeItem> items) {
        this.items = items;
    }
}
