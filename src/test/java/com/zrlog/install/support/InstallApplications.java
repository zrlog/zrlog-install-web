package com.zrlog.install.support;

import com.hibegin.http.server.config.ServerConfig;
import com.zrlog.install.business.service.InstallProgressListener;
import com.zrlog.install.business.service.InstallService;
import com.zrlog.install.business.vo.InstallConfigVO;
import com.zrlog.install.web.config.InstallConfig;
import com.zrlog.install.web.config.InstallWebBindings;
import com.zrlog.install.web.di.InstallComponent;

import java.util.IdentityHashMap;
import java.util.Map;

/** Per-test applications assembled by the same entry point as standalone and hosted installers. */
public final class InstallApplications {
    private final Map<InstallConfig, InstallComponent> components = new IdentityHashMap<>();

    public synchronized InstallComponent component(InstallConfig config) {
        return components.computeIfAbsent(config, key -> {
            ServerConfig server = new ServerConfig();
            InstallWebBindings.configure(server, key);
            return server.getBindings().build().get(InstallComponent.class);
        });
    }

    public InstallService service(InstallConfig config, InstallConfigVO request) {
        return service(config, request, InstallProgressListener.NONE);
    }

    public InstallService service(InstallConfig config, InstallConfigVO request, InstallProgressListener progress) {
        return service(config, request, progress, config.getAcceptLanguage());
    }

    public InstallService service(InstallConfig config, InstallConfigVO request,
                                  InstallProgressListener progress, String language) {
        return component(config).installers().create(request, progress, language);
    }
}
