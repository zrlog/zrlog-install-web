package com.zrlog.install.web.config;

import com.hibegin.http.server.api.HttpErrorHandle;
import com.hibegin.http.server.config.ServerConfig;
import com.zrlog.install.business.service.InstallOperationState;
import com.zrlog.install.business.service.InstallResourceService;
import com.zrlog.install.web.controller.api.ApiInstallController;
import com.zrlog.install.web.controller.api.ApiMigrateController;
import com.zrlog.install.web.controller.page.InstallController;
import com.zrlog.install.web.di.DaggerInstallComponent;
import com.zrlog.install.web.di.InstallComponent;
import com.zrlog.install.web.interceptor.BlogInstallInterceptor;

/** Installer-owned assembly, shared by the standalone entry point and application hosts. */
public final class InstallWebBindings {

    private InstallWebBindings() { }

    public static void configure(ServerConfig server, InstallConfig config) {
        InstallComponent component = DaggerInstallComponent.factory().create(config);
        server.getBindings()
                .instance(InstallConfig.class, config)
                .instance(InstallComponent.class, component)
                .singleton(InstallOperationState.class, r -> component.operations())
                .singleton(InstallResourceService.class, r -> component.resources())
                .controller(
                        ApiInstallController.class,
                        (r, request, response) -> component.installApi())
                .controller(
                        ApiMigrateController.class,
                        (r, request, response) -> component.migrateApi())
                .controller(InstallController.class, (r, request, response) -> component.page())
                .interceptor(BlogInstallInterceptor.class, r -> component.interceptor());
        InstallRouters.configRouter(server);
        server.addInterceptor(BlogInstallInterceptor.class);

        HttpErrorHandle installError = config.getErrorHandler();
        for (int status : new int[]{400, 403, 500}) {
            HttpErrorHandle fallback = server.getErrorHandle(status);
            server.addErrorHandle(status, (request, response, error) -> {
                String uri = request == null ? null : request.getUri();
                if ("/api/install".equals(uri) || uri != null && uri.startsWith("/api/install/")) {
                    installError.doHandle(request, response, error);
                } else if (fallback != null) {
                    fallback.doHandle(request, response, error);
                } else {
                    response.renderCode(status);
                }
            });
        }
    }
}
