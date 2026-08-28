package com.zrlog.install.web.controller.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.web.Controller;
import com.zrlog.install.business.response.InstallProbeResponse;
import com.zrlog.install.business.response.InstallResourceResponse;
import com.zrlog.install.business.response.InstallResultResponse;
import com.zrlog.install.business.response.InstallProgressEvent;
import com.zrlog.install.business.service.InstallProgressListener;
import com.zrlog.install.business.service.InstallProbeService;
import com.zrlog.install.business.response.TestConnectResponse;
import com.zrlog.install.business.service.LocalSqliteSupport;
import com.zrlog.install.business.service.InstallOperationLock;
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
import com.zrlog.install.util.InstallErrorResponsePolicy;
import com.zrlog.install.util.InstallSseEmitter;
import com.zrlog.install.util.InstallSuccessContentUtils;
import com.zrlog.install.util.StringUtils;
import com.zrlog.install.web.InstallConstants;
import com.zrlog.install.web.InstallRequestSecurity;
import com.zrlog.install.web.InstallRequestValidator;
import com.zrlog.install.web.config.InstallConfig;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 与安装向导相关的路由进行控制
 * 注意 install.lock 文件相当重要，如果不是重新安装请不要删除这个自动生成的文件
 */
public class ApiInstallController extends Controller {

    private static final AtomicReference<InstallOperation> RUNNING_OPERATION = new AtomicReference<>();
    private final InstallConfig installConfig;

    public ApiInstallController() {
        this.installConfig = InstallConstants.installConfig;
    }

    /**
     * 检查数据库是否可以正常连接使用，无法连接时给出相应的提示
     */
    @ResponseBody
    public TestConnectResponse testDbConn() {
        InstallRequestSecurity.assertMutationRequest(request);
        RequestParameters parameters = readRequestParameters();
        InstallConfigVO configVO = new InstallConfigVO();
        configVO.setDbConfig(getDbConn(parameters));
        configVO.setContextPath(request.getContextPath());
        TestConnectDbResult testConnectDbResult = new InstallService(installConfig, configVO).testDbConn();
        if (testConnectDbResult.getError() != 0) {
            throw new InstallException(testConnectDbResult);
        }
        return new TestConnectResponse();
    }

    protected InstallDatabaseConfig getDbConn() {
        return getDbConn(readRequestParameters());
    }

    private InstallDatabaseConfig getDbConn(RequestParameters parameters) {
        InstallRequestValidator.validateDatabase(parameters.asMap());
        String dbType = parameters.getTrimmed("dbType", "mysql").toLowerCase(Locale.ROOT);
        if ("sqlite".equalsIgnoreCase(dbType)) {
            if (!LocalSqliteSupport.isAvailable(installConfig)) {
                throw new InstallException(TestConnectDbResult.UNSUPPORTED_DATABASE);
            }
            return LocalSqliteSupport.createDatabaseConfig(installConfig);
        }
        InstallDatabaseConfig dbConn = new InstallDatabaseConfig();
        dbConn.setUser(parameters.getTrimmed("dbUserName", ""));
        dbConn.setPassword(parameters.get("dbPassword", ""));
        dbConn.setDbType(dbType);
        dbConn.setDbHost(parameters.getTrimmed("dbHost", ""));
        dbConn.setDbPort(parameters.getTrimmed("dbPort", ""));
        dbConn.setDbName(parameters.getTrimmed("dbName", ""));
        String jdbcUrl = "jdbc:" + dbType + "://" + dbConn.getDbHost() + ":"
                + dbConn.getDbPort() + "/" + dbConn.getDbName();
        String jdbcUrlQueryStr = installConfig.getJdbcUrlQueryStr(dbType, parameters.toJdbcUrlParamMap());
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
        InstallRequestSecurity.assertMutationRequest(request);
        RequestParameters parameters = readRequestParameters();
        InstallRequestValidator.validateDatabase(parameters.asMap());
        InstallRequestValidator.validateSite(parameters.asMap());
        InstallSiteConfig configMsg = new InstallSiteConfig();
        configMsg.setTitle(parameters.getTrimmed("title", ""));
        configMsg.setSecondTitle(parameters.getTrimmed("second_title", ""));
        configMsg.setUsername(parameters.getTrimmed("username", ""));
        configMsg.setPassword(parameters.get("password", ""));
        configMsg.setEmail(parameters.getTrimmed("email", ""));
        InstallConfigVO configVO = new InstallConfigVO();
        configVO.setConfigMsg(configMsg);
        configVO.setDbConfig(getDbConn(parameters));
        configVO.setContextPath(request.getContextPath());
        if (isSseRequest()) {
            writeInstallStream(configVO);
            return;
        }
        if (!executeInstall(configVO, InstallProgressListener.NONE)) {
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
        InstallRequestSecurity.assertMutationRequest(request);
        readRequestParameters();
        writeUpgradeStream();
    }

    @ResponseBody
    public InstallResultResponse resumeInstall() {
        InstallRequestSecurity.assertMutationRequest(request);
        readRequestParameters();
        InstallOperation blockingOperation = acquireOperation(InstallOperation.INSTALL);
        if (blockingOperation != null) {
            throw new InstallOperationInProgressException(blockingOperation.name());
        }
        try {
            if (installConfig.getAction().isInstalled()) {
                throw new InstalledException();
            }
            InstallConfigVO configVO = new InstallConfigVO();
            configVO.setContextPath(request.getContextPath());
            if (!new InstallService(installConfig, configVO).resume()) {
                throw new InstallRecoveryUnavailableException();
            }
            return buildInstallResultResponse();
        } finally {
            releaseOperation(InstallOperation.INSTALL);
        }
    }

    @ResponseBody
    public InstallResultResponse installCompletion() throws IOException {
        InstallRequestSecurity.assertMutationRequest(request);
        readRequestParameters();
        InstallOperationLock operationLock = InstallOperationLock.tryAcquire(
                installConfig.getAction().getLockFile());
        if (operationLock == null) {
            throw new InstallOperationInProgressException(InstallOperation.INSTALL.name());
        }
        try (InstallOperationLock ignored = operationLock) {
            File dbPropertiesFile = installConfig.getDbPropertiesFile();
            if (!installConfig.getAction().isInstalled() || !installConfig.isAskConfig()
                    || !dbPropertiesFile.isFile() || !dbPropertiesFile.canRead()) {
                String message = com.zrlog.install.util.InstallI18nUtil.getInstallStringFromRes(
                        "installCompletionUnavailable");
                throw new InvalidInstallRequestException(message.isEmpty()
                        ? "Invalid installation request" : message);
            }
            return buildInstallResultResponse();
        }
    }

    protected void writeUpgradeStream() throws IOException {
        InstallSseEmitter.write(response, "install-upgrade", "upgrade-error", emitter -> {
            InstallOperation blockingOperation = acquireOperation(InstallOperation.UPGRADE);
            if (blockingOperation != null) {
                sendUpgradeOperationInProgress(emitter, blockingOperation);
                return;
            }
            try {
                InstallOperationLock operationLock = InstallOperationLock.tryAcquire(
                        installConfig.getAction().getLockFile());
                if (operationLock == null) {
                    sendUpgradeOperationInProgress(emitter, InstallOperation.INSTALL);
                    return;
                }
                try (InstallOperationLock ignored = operationLock) {
                    if (installConfig.getAction().isInstalled()) {
                        emitter.send("upgrade-error", new InstallUpgradeResult(
                                false, "ZrLog is already installed"));
                        return;
                    }
                    InstallUpgradeAction upgradeAction = installConfig.getUpgradeAction();
                    if (!upgradeAction.isSupported()) {
                        emitter.send("upgrade-error", new InstallUpgradeResult(false,
                                "Online upgrade is not supported by this package"));
                        return;
                    }
                    InstallUpgradeResult result = upgradeAction.upgrade(emitter::send);
                    emitter.send(result.isFinish() ? "upgrade-complete" : "upgrade-error", result);
                }
            } finally {
                releaseOperation(InstallOperation.UPGRADE);
            }
        });
    }

    private static void sendUpgradeOperationInProgress(InstallSseEmitter emitter,
                                                       InstallOperation blockingOperation) throws IOException {
        InstallOperationInProgressException exception =
                new InstallOperationInProgressException(blockingOperation.name());
        emitter.send("upgrade-error", new InstallUpgradeResult(
                false, InstallErrorResponsePolicy.controlledMessage(exception), exception.getCode()));
    }

    protected void writeInstallStream(InstallConfigVO configVO) throws IOException {
        InstallSseEmitter.write(response, "install-start", "install-error", emitter -> {
            AtomicBoolean errorSent = new AtomicBoolean(false);
            boolean installed;
            try {
                installed = executeInstall(configVO, event -> {
                    if (Objects.equals(event.getStatus(), "error")) {
                        errorSent.set(true);
                        emitter.send("install-error", event);
                        return;
                    }
                    emitter.send("install-progress", event);
                });
            } catch (AbstractInstallException e) {
                if (!InstallErrorResponsePolicy.isControlled(e)) {
                    throw e;
                }
                String code = e instanceof InstallErrorCodeProvider
                        ? ((InstallErrorCodeProvider) e).getCode() : "install";
                emitter.send("install-error", InstallProgressEvent.error(
                        code, InstallErrorResponsePolicy.controlledMessage(e)));
                return;
            }
            if (!installed) {
                if (!errorSent.get()) {
                    emitter.send("install-error", InstallProgressEvent.error("install", "Install failed"));
                }
                return;
            }
            emitter.send("install-complete", buildInstallResultResponse());
        });
    }

    private boolean executeInstall(InstallConfigVO configVO, InstallProgressListener progressListener) {
        InstallOperation blockingOperation = acquireOperation(InstallOperation.INSTALL);
        if (blockingOperation != null) {
            throw new InstallOperationInProgressException(blockingOperation.name());
        }
        try {
            if (installConfig.getAction().isInstalled()) {
                throw new InstalledException();
            }
            return new InstallService(installConfig, configVO, progressListener).install();
        } finally {
            releaseOperation(InstallOperation.INSTALL);
        }
    }

    private static InstallOperation acquireOperation(InstallOperation requestedOperation) {
        while (true) {
            InstallOperation runningOperation = RUNNING_OPERATION.get();
            if (runningOperation != null) {
                return runningOperation;
            }
            if (RUNNING_OPERATION.compareAndSet(null, requestedOperation)) {
                return null;
            }
        }
    }

    private static void releaseOperation(InstallOperation operation) {
        RUNNING_OPERATION.compareAndSet(operation, null);
    }

    protected InstallResultResponse buildInstallResultResponse() {
        return new InstallResultResponse(new InstallSuccessData(InstallSuccessContentUtils.getContent(installConfig.getDbPropertiesFile(), installConfig.isAskConfig(), request.getServerConfig())));
    }

    protected boolean isSseRequest() {
        String accept = request.getHeader("Accept");
        return Objects.nonNull(accept) && accept.contains("text/event-stream");
    }

    private RequestParameters readRequestParameters() {
        return RequestParameters.from(getRequest());
    }

    private static class RequestParameters {

        private final Map<String, String> jsonValues;

        private RequestParameters(Map<String, String> jsonValues) {
            this.jsonValues = jsonValues;
        }

        static RequestParameters from(HttpRequest request) {
            if (!InstallRequestSecurity.isJsonContentType(request.getHeader("Content-Type"))) {
                throw new InvalidInstallRequestException("Invalid installation request");
            }
            ByteBuffer bodyBuffer = request.getRequestBodyByteBuffer();
            if (bodyBuffer == null || !bodyBuffer.hasRemaining()) {
                throw new InvalidInstallRequestException("Invalid installation request");
            }
            ByteBuffer bodyCopy = bodyBuffer.asReadOnlyBuffer();
            byte[] bodyBytes = new byte[bodyCopy.remaining()];
            bodyCopy.get(bodyBytes);
            String body = new String(bodyBytes, StandardCharsets.UTF_8).trim();
            if (body.isEmpty()) {
                throw new InvalidInstallRequestException("Invalid installation request");
            }
            try {
                JsonElement root = JsonParser.parseString(body);
                if (!root.isJsonObject()) {
                    throw new InvalidInstallRequestException("JSON request body must be an object");
                }
                Map<String, String> values = new LinkedHashMap<>();
                JsonObject jsonObject = root.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                    JsonElement value = entry.getValue();
                    if (value == null || value.isJsonNull()) {
                        values.put(entry.getKey(), null);
                    } else if (value.isJsonPrimitive()) {
                        values.put(entry.getKey(), value.getAsString());
                    } else {
                        throw new InvalidInstallRequestException(
                                "JSON request field must be a primitive value");
                    }
                }
                return new RequestParameters(values);
            } catch (JsonParseException | IllegalStateException e) {
                throw new InvalidInstallRequestException("Invalid JSON request body");
            }
        }

        String get(String key) {
            return jsonValues.get(key);
        }

        String get(String key, String defaultValue) {
            return Objects.requireNonNullElse(jsonValues.get(key), defaultValue);
        }

        String getTrimmed(String key, String defaultValue) {
            return get(key, defaultValue).trim();
        }

        Map<String, String> asMap() {
            return java.util.Collections.unmodifiableMap(jsonValues);
        }

        Map<String, String[]> toJdbcUrlParamMap() {
            Map<String, String[]> values = new LinkedHashMap<>();
            jsonValues.forEach((key, value) -> {
                if (!key.toLowerCase(Locale.ROOT).contains("password")) {
                    values.put(key, new String[]{Objects.requireNonNullElse(value, "")});
                }
            });
            return values;
        }
    }

    private enum InstallOperation {
        INSTALL,
        UPGRADE
    }
}
