package com.zrlog.install.web.di;

import com.zrlog.install.business.service.*;
import com.zrlog.install.web.config.InstallConfig;
import com.zrlog.install.web.controller.api.*;
import com.zrlog.install.web.controller.page.InstallController;
import com.zrlog.install.web.interceptor.BlogInstallInterceptor;

import dagger.BindsInstance;
import dagger.Component;

import jakarta.inject.Singleton;

@Singleton
@Component(modules = InstallModule.class)
public interface InstallComponent {
    InstallOperationState operations();

    InstallResourceService resources();

    InstallService.Factory installers();

    ApiInstallController installApi();

    ApiMigrateController migrateApi();

    InstallController page();

    BlogInstallInterceptor interceptor();

    @Component.Factory
    interface Factory {
        InstallComponent create(@BindsInstance InstallConfig config);
    }
}
