package com.zrlog.install.business.service;

import com.zrlog.install.business.response.InstallUpgradeResult;

public interface InstallUpgradeAction {

    InstallUpgradeAction UNSUPPORTED = new InstallUpgradeAction() {
        @Override
        public boolean isSupported() {
            return false;
        }

        @Override
        public InstallUpgradeResult upgrade(ProgressListener progressListener) {
            return new InstallUpgradeResult(false, "Online upgrade is not supported by this package");
        }
    };

    boolean isSupported();

    InstallUpgradeResult upgrade(ProgressListener progressListener) throws Exception;

    @FunctionalInterface
    interface ProgressListener {

        void onProgress(String event, Object data) throws Exception;
    }
}
