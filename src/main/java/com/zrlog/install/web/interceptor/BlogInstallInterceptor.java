package com.zrlog.install.web.interceptor;

import com.hibegin.http.server.api.HandleAbleInterceptor;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.web.MethodInterceptor;
import com.zrlog.install.exception.InstalledException;
import com.zrlog.install.web.config.InstallConfig;

import jakarta.inject.Inject;

import java.util.Objects;

public class BlogInstallInterceptor implements HandleAbleInterceptor {

    private final InstallConfig config;

    @Inject
    public BlogInstallInterceptor(InstallConfig config) {
        this.config = Objects.requireNonNull(config);
    }

    private static final String INSTALL_STATIC_PREFIX = "/install/static/";
    private static final String INSTALL_COMPLETION_PATH = "/api/install/installCompletion";

    @Override
    public boolean isHandleAble(HttpRequest request) {
        if (request.getUri().startsWith(INSTALL_STATIC_PREFIX)) {
            return true;
        }
        return Objects.equals(request.getUri(), "/install") ||
                request.getUri().startsWith("/api/install/");
    }

    private boolean isSkipCheck(HttpRequest request) {
        if (request.getUri().startsWith(INSTALL_STATIC_PREFIX)) {
            return true;
        }
        return Objects.equals(request.getUri(), "/api/install/installResource")
                || Objects.equals(request.getUri(), INSTALL_COMPLETION_PATH);
    }

    @Override
    public boolean doInterceptor(HttpRequest request, HttpResponse response) throws Exception {
        if (!request.getUri().startsWith(INSTALL_STATIC_PREFIX)) {
            response.getHeader().put("Cache-Control", "no-store");
            response.getHeader().put("Pragma", "no-cache");
        }
        if (isSkipCheck(request)) {
            new MethodInterceptor().doInterceptor(request, response);
            return false;
        }
        String target = request.getUri();
        if (target.startsWith("/api/install/") && config.getAction().isInstalled()) {
            throw new InstalledException(config.isWarMode(), config.getAcceptLanguage());
        }
        new MethodInterceptor().doInterceptor(request, response);
        return false;
    }
}
