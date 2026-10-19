package com.zrlog.install.web.config;

import com.hibegin.http.server.api.HttpErrorHandle;
import com.hibegin.http.server.config.ServerConfig;
import com.zrlog.install.business.service.InstallOperationState;
import com.zrlog.install.business.service.InstallResourceService;
import com.zrlog.install.web.controller.api.ApiInstallController;
import com.zrlog.install.web.controller.api.ApiMigrateController;
import com.zrlog.install.web.controller.page.InstallController;
import com.zrlog.install.web.interceptor.BlogInstallInterceptor;

/** Installer-owned assembly, shared by the standalone entry point and application hosts. */
public final class InstallWebBindings {

    private InstallWebBindings() { }

    public static void configure(ServerConfig server, InstallConfig config) {
        server.getBindings()
                .instance(InstallConfig.class, config)
                .instance(InstallOperationState.class, new InstallOperationState())
                .singleton(InstallResourceService.class, r -> new InstallResourceService(config))
                .controller(ApiInstallController.class, (r, request, response) -> new ApiInstallController(
                        config, r.get(InstallResourceService.class), r.get(InstallOperationState.class)))
                .controller(ApiMigrateController.class, (r, request, response) -> new ApiMigrateController(config.getAction()))
                .controller(InstallController.class, (r, request, response) -> new InstallController(
                        r.get(InstallResourceService.class), config.getAcceptLanguage()))
                .interceptor(BlogInstallInterceptor.class, r -> new BlogInstallInterceptor(config));
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
