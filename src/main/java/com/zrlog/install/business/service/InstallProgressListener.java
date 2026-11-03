package com.zrlog.install.business.service;

import com.zrlog.install.business.response.InstallProgressEvent;

@FunctionalInterface
public interface InstallProgressListener {

    InstallProgressListener NONE = event -> {};

    void onProgress(InstallProgressEvent event) throws Exception;
}
