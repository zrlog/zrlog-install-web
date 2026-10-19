package com.zrlog.install.web.di;

import com.zrlog.install.web.InstallAction;
import com.zrlog.install.web.config.InstallConfig;

import dagger.Module;
import dagger.Provides;

@Module
public final class InstallModule {
    private InstallModule() {}

    @Provides
    static InstallAction action(InstallConfig config) {
        return config.getAction();
    }

    @Provides
    static String language(InstallConfig config) {
        return config.getAcceptLanguage();
    }
}
