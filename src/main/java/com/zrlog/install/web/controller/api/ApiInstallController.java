package com.zrlog.install.web.controller.api;

import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.web.Controller;
import com.zrlog.install.business.response.InstallProbeResponse;
import com.zrlog.install.business.response.InstallResourceResponse;
import com.zrlog.install.business.response.InstallResultResponse;
import com.zrlog.install.business.response.InstallProgressEvent;
import com.zrlog.install.business.service.InstallProbeService;
import com.zrlog.install.business.response.TestConnectResponse;
import com.zrlog.install.business.service.InstallResourceService;
import com.zrlog.install.business.service.InstallService;
import com.zrlog.install.business.service.InstallUpgradeAction;
import com.zrlog.install.business.response.InstallUpgradeResult;
import com.zrlog.install.business.type.TestConnectDbResult;
import com.zrlog.install.business.vo.InstallConfigVO;
import com.zrlog.install.business.vo.InstallDatabaseConfig;
import com.zrlog.install.business.vo.InstallSiteConfig;
import com.zrlog.install.business.vo.InstallSuccessData;
import com.zrlog.install.exception.*;
import com.zrlog.install.util.InstallSseEmitter;
import com.zrlog.install.util.InstallSuccessContentUtils;
import com.zrlog.install.util.StringUtils;
import com.zrlog.install.web.InstallConstants;
import com.zrlog.install.web.config.InstallConfig;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 与安装向导相关的路由进行控制
 * 注意 install.lock 文件相当重要，如果不是重新安装请不要删除这个自动生成的文件
 */
public class ApiInstallController extends Controller {

    private static final AtomicBoolean UPGRADE_RUNNING = new AtomicBoolean(false);
    private final InstallConfig installConfig;

    public ApiInstallController() {
        this.installConfig = InstallConstants.installConfig;
    }

    /**
     * 检查数据库是否可以正常连接使用，无法连接时给出相应的提示
     */
    @ResponseBody
    public TestConnectResponse testDbConn() {
        InstallConfigVO configVO = new InstallConfigVO();
        configVO.setDbConfig(getDbConn());
        configVO.setContextPath(request.getContextPath());
        TestConnectDbResult testConnectDbResult = new InstallService(installConfig, configVO).testDbConn();
        if (testConnectDbResult.getError() != 0) {
            throw new InstallException(testConnectDbResult);
        }
        return new TestConnectResponse();
    }

    protected InstallDatabaseConfig getDbConn() {
        if (StringUtils.isEmpty(getRequest().getParaToStr("dbHost"))) {
            throw new MissingDbHostException();
        }
        if (StringUtils.isEmpty(getRequest().getParaToStr("dbPort"))) {
            throw new MissingDbPortException();
        }
        if (StringUtils.isEmpty(getRequest().getParaToStr("dbUserName"))) {
            throw new MissingDbUserNameException();
        }
        if (StringUtils.isEmpty(getRequest().getParaToStr("dbName"))) {
            throw new MissingDbNameException();
        }
        InstallDatabaseConfig dbConn = new InstallDatabaseConfig();
        dbConn.setUser(getRequest().getParaToStr("dbUserName", ""));
        dbConn.setPassword(getRequest().getParaToStr("dbPassword", ""));
        String dbType = getRequest().getParaToStr("dbType", "mysql");
        dbConn.setDbType(dbType);
        dbConn.setDbHost(getRequest().getParaToStr("dbHost"));
        dbConn.setDbPort(getRequest().getParaToStr("dbPort"));
        dbConn.setDbName(getRequest().getParaToStr("dbName"));
        String jdbcUrl = "jdbc:" + dbType + "://" + getRequest().getParaToStr("dbHost") + ":" + getRequest().getParaToStr("dbPort") + "/" + getRequest().getParaToStr("dbName");
        String jdbcUrlQueryStr = InstallConstants.installConfig.getJdbcUrlQueryStr(dbType, getRequest().getParamMap());
        if (Objects.equals(dbType, "mysql")) {
            dbConn.setDriverClass("com.mysql.cj.jdbc.Driver");
        }
        dbConn.setJdbcUrl(jdbcUrl + (StringUtils.isEmpty(jdbcUrlQueryStr) ? "" : "?" + jdbcUrlQueryStr));
        return dbConn;
    }

    /**
     * 数据库检查通过后，根据填写信息，执行数据表，表数据的初始化
     */
    @ResponseBody
    public void startInstall() throws IOException {
        InstallSiteConfig configMsg = new InstallSiteConfig();
        configMsg.setTitle(getRequest().getParaToStr("title", ""));
        configMsg.setSecondTitle(getRequest().getParaToStr("second_title", ""));
        configMsg.setUsername(getRequest().getParaToStr("username", ""));
        configMsg.setPassword(getRequest().getParaToStr("password", ""));
        configMsg.setEmail(getRequest().getParaToStr("email", ""));
        InstallConfigVO configVO = new InstallConfigVO();
        configVO.setConfigMsg(configMsg);
        configVO.setDbConfig(getDbConn());
        configVO.setContextPath(request.getContextPath());
        if (isSseRequest()) {
            writeInstallStream(configVO);
            return;
        }
        if (!new InstallService(installConfig, configVO).install()) {
            throw new InstallException(TestConnectDbResult.UNKNOWN);
        }
        response.renderJson(buildInstallResultResponse());
    }

    @ResponseBody
    public InstallResourceResponse installResource() {
        return new InstallResourceResponse(new InstallResourceService().installResourceInfo(getRequest()));
    }

    @ResponseBody
    public InstallProbeResponse probe() {
        return new InstallProbeResponse(new InstallProbeService().probe(installConfig));
    }

    @ResponseBody
    public void startUpgrade() throws IOException {
        writeUpgradeStream();
    }

    protected void writeUpgradeStream() throws IOException {
        InstallSseEmitter.write(response, "install-upgrade", "upgrade-error", emitter -> {
            if (installConfig.getAction().isInstalled()) {
                emitter.send("upgrade-error", new InstallUpgradeResult(false, "ZrLog is already installed"));
                return;
            }
            InstallUpgradeAction upgradeAction = installConfig.getUpgradeAction();
            if (!upgradeAction.isSupported()) {
                emitter.send("upgrade-error", new InstallUpgradeResult(false,
                        "Online upgrade is not supported by this package"));
                return;
            }
            if (!UPGRADE_RUNNING.compareAndSet(false, true)) {
                emitter.send("upgrade-error", new InstallUpgradeResult(false, "An upgrade is already running"));
                return;
            }
            try {
                InstallUpgradeResult result = upgradeAction.upgrade(emitter::send);
                emitter.send(result.isFinish() ? "upgrade-complete" : "upgrade-error", result);
            } finally {
                UPGRADE_RUNNING.set(false);
            }
        });
    }

    protected void writeInstallStream(InstallConfigVO configVO) throws IOException {
        InstallSseEmitter.write(response, "install-start", "install-error", emitter -> {
            AtomicBoolean errorSent = new AtomicBoolean(false);
            boolean installed = new InstallService(installConfig, configVO, event -> {
                if (Objects.equals(event.getStatus(), "error")) {
                    errorSent.set(true);
                    emitter.send("install-error", event);
                    return;
                }
                emitter.send("install-progress", event);
            }).install();
            if (!installed) {
                if (!errorSent.get()) {
                    emitter.send("install-error", InstallProgressEvent.error("install", "Install failed"));
                }
                return;
            }
            emitter.send("install-complete", buildInstallResultResponse());
        });
    }

    protected InstallResultResponse buildInstallResultResponse() {
        return new InstallResultResponse(new InstallSuccessData(InstallSuccessContentUtils.getContent(installConfig.getDbPropertiesFile(), installConfig.isAskConfig(), request.getServerConfig())));
    }

    protected boolean isSseRequest() {
        String accept = request.getHeader("Accept");
        return Objects.nonNull(accept) && accept.contains("text/event-stream");
    }
}
