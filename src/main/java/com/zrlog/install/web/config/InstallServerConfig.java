package com.zrlog.install.web.config;

import com.hibegin.http.server.config.AbstractServerConfig;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.web.MethodInterceptor;

public class InstallServerConfig extends AbstractServerConfig {

    private final ServerConfig serverConfig = new ServerConfig();

    public InstallServerConfig(InstallConfig config) {
        for (int status : new int[]{400, 403, 500}) {
            serverConfig.addErrorHandle(status, config.getErrorHandler());
        }
        InstallWebBindings.configure(serverConfig, config);
        //默认
        serverConfig.addInterceptor(MethodInterceptor.class);
        serverConfig.setPort(6080);
    }

    @Override
    public ServerConfig getServerConfig() {
        return serverConfig;
    }

    @Override
    public RequestConfig getRequestConfig() {
        return new RequestConfig();
    }

    @Override
    public ResponseConfig getResponseConfig() {
        return new ResponseConfig();
    }
}
