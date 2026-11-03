package com.zrlog.install.web.config;

import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.web.Router;
import com.zrlog.install.web.controller.api.ApiInstallController;
import com.zrlog.install.web.controller.api.ApiMigrateController;
import com.zrlog.install.web.controller.page.InstallController;

public class InstallRouters {

    public static void configRouter(ServerConfig serverConfig) {
        Router router = serverConfig.getRouter();
        router.addMapper("/install", InstallController.class);
        router.addMapper("/api/install", ApiInstallController.class);
        router.addMapper("/api/install/migrate", ApiMigrateController.class);
        serverConfig.addStaticResourceMapper(
                "/install/static", "/install/static", InstallRouters.class::getResourceAsStream);
    }
}
