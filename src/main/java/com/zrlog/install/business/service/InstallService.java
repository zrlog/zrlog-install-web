package com.zrlog.install.business.service;

import com.hibegin.common.dao.DAO;
import com.hibegin.common.dao.DataSourceWrapperImpl;
import com.hibegin.common.dao.SqlConvertUtils;
import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.template.BasicTemplateRender;
import com.zrlog.install.business.response.InstallProgressEvent;
import com.zrlog.install.business.type.TestConnectDbResult;
import com.zrlog.install.business.vo.InstallDatabaseConfig;
import com.zrlog.install.business.vo.DefaultWebsiteSettings;
import com.zrlog.install.business.vo.InstallConfigVO;
import com.zrlog.install.business.vo.InstallSiteConfig;
import com.zrlog.install.exception.AbstractInstallException;
import com.zrlog.install.exception.InstallException;
import com.zrlog.install.exception.InstallOperationInProgressException;
import com.zrlog.install.exception.InstalledException;
import com.zrlog.install.util.InstallI18nUtil;
import com.zrlog.install.util.InstallLogUtil;
import com.zrlog.install.util.StringUtils;
import com.zrlog.install.web.InstallAction;
import com.zrlog.install.web.config.InstallConfig;
import org.jsoup.Jsoup;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLSyntaxErrorException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 与安装向导相关的业务代码
 */
public class InstallService {

    private static final Logger LOGGER = LoggerUtil.getLogger(InstallService.class);
    private final InstallDatabaseConfig dbConn;
    private final InstallSiteConfig configMsg;
    private final Map<String, String> appendWebsite;
    private final InstallAction installAction;
    private final InstallConfig installConfig;
    private final String contextPath;
    private final InstallProgressListener progressListener;
    private final InstallStateStore installStateStore = new InstallStateStore();
    private final InstallRecoveryStore installRecoveryStore = new InstallRecoveryStore();

    public InstallService(InstallConfig installConfig, InstallConfigVO installConfigVO) {
        this(installConfig, installConfigVO, InstallProgressListener.NONE);
    }

    public InstallService(InstallConfig installConfig, InstallConfigVO installConfigVO,
                          InstallProgressListener progressListener) {
        InstallDatabaseConfig requestedDbConn = Objects.requireNonNullElseGet(
                installConfigVO.getDbConfig(), InstallDatabaseConfig::new);
        this.dbConn = requestedDbConn.isLocalSqlite()
                ? LocalSqliteSupport.createDatabaseConfig(installConfig)
                : requestedDbConn;
        this.configMsg = Objects.requireNonNullElseGet(installConfigVO.getConfigMsg(), InstallSiteConfig::new);
        this.appendWebsite = installConfigVO.getAppendWebsite();
        this.installAction = installConfig.getAction();
        this.installConfig = installConfig;
        this.contextPath = Objects.requireNonNullElse(installConfigVO.getContextPath(), "");
        this.progressListener = Objects.requireNonNullElse(progressListener, InstallProgressListener.NONE);
    }

    /**
     * 通过执行数据库的sql文件，完成对数据库表，基础表数据的初始化，达到安装的效果
     *
     * @return false 表示安装没有正常执行，true 表示初始化数据库成功。
     */
    public boolean install() {
        if (installAction.isInstalled()) {
            throw new InstalledException();
        }
        if (dbConn.isLocalSqlite() && !LocalSqliteSupport.isAvailable(installConfig)) {
            emitError("database", new IllegalStateException("Local SQLite is not supported by this package"));
            return false;
        }
        return startInstall(dbConn, configMsg);
    }

    public boolean resume() {
        if (installAction.isInstalled()) {
            throw new InstalledException();
        }
        return resumeInstall();
    }

    DefaultWebsiteSettings getDefaultWebSiteSettings(InstallSiteConfig webSite) {
        return DefaultWebsiteSettings.from(webSite, installConfig, appendWebsite);
    }

    static DataSourceWrapperImpl buildDataSource(Properties dbProperties, boolean dev) throws ClassNotFoundException {
        DataSourceWrapperImpl dataSource = new DataSourceWrapperImpl(dbProperties, dev);
        if (!dataSource.isWebApi()) {
            String driverClass = dbProperties.getProperty("driverClass");
            if (StringUtils.isEmpty(driverClass)) {
                throw new ClassNotFoundException("Missing JDBC driverClass");
            }
            Class.forName(driverClass);
            dataSource.setDriverClassName(driverClass);
            dataSource.setJdbcUrl(dbProperties.getProperty("jdbcUrl"));
        }
        dataSource.setUsername(dbProperties.getProperty("user"));
        dataSource.setPassword(dbProperties.getProperty("password"));

        return dataSource;
        /*}*/
    }

    /**
     * 尝试使用填写的数据库信息连接数据库
     */
    public TestConnectDbResult testDbConn() {
        if (dbConn.isLocalSqlite() && !LocalSqliteSupport.isAvailable(installConfig)) {
            return TestConnectDbResult.UNSUPPORTED_DATABASE;
        }
        final LocalSqliteSupport.ConnectionProbe connectionProbe;
        final InstallDatabaseConfig testedDatabaseConfig;
        try {
            if (dbConn.isLocalSqlite()) {
                if (LocalSqliteSupport.databaseTargetExists(installConfig)) {
                    return TestConnectDbResult.DATABASE_NOT_EMPTY;
                }
                connectionProbe = LocalSqliteSupport.createConnectionProbe(installConfig);
                testedDatabaseConfig = connectionProbe.getDatabaseConfig();
            } else {
                connectionProbe = null;
                testedDatabaseConfig = dbConn;
            }
        } catch (IOException e) {
            InstallLogUtil.logFailure(LOGGER, Level.SEVERE,
                    InstallLogUtil.FailurePhase.LOCAL_SQLITE_PREPARATION, e);
            return TestConnectDbResult.CREATE_CONNECT_ERROR;
        }
        Properties properties = testedDatabaseConfig.toProperties();
        try (LocalSqliteSupport.ConnectionProbe ignored = connectionProbe;
             DataSourceWrapperImpl ds = buildDataSource(properties, EnvKit.isDevMode())) {
            ds.testConnection();
            if (InstallSchemaSafety.containsInstallTable(ds, testedDatabaseConfig)) {
                return TestConnectDbResult.DATABASE_NOT_EMPTY;
            }
            return TestConnectDbResult.SUCCESS;
        } catch (ClassNotFoundException e) {
            InstallLogUtil.logFailure(LOGGER, Level.SEVERE,
                    InstallLogUtil.FailurePhase.DATABASE_CONNECTION_TEST, e);
            return TestConnectDbResult.MISSING_JDBC_DRIVER;
        } catch (SQLRecoverableException e) {
            InstallLogUtil.logFailure(LOGGER, Level.SEVERE,
                    InstallLogUtil.FailurePhase.DATABASE_CONNECTION_TEST, e);
            return TestConnectDbResult.CREATE_CONNECT_ERROR;
        } catch (SQLSyntaxErrorException e) {
            InstallLogUtil.logFailure(LOGGER, Level.SEVERE,
                    InstallLogUtil.FailurePhase.DATABASE_CONNECTION_TEST, e);
            if ("mysql".equals(dbConn.getDbType()) && messageContainsAll(e, "Unknown database")) {
                try {
                    if (createDatabase()) {
                        return TestConnectDbResult.SUCCESS;
                    }
                } catch (Exception createEx) {
                    InstallLogUtil.logFailure(LOGGER, Level.SEVERE,
                            InstallLogUtil.FailurePhase.DATABASE_CREATION, createEx);
                }
            }
            return TestConnectDbResult.DB_NOT_EXISTS;
        } catch (SQLException e) {
            InstallLogUtil.logFailure(LOGGER, Level.SEVERE,
                    InstallLogUtil.FailurePhase.DATABASE_CONNECTION_TEST, e);
            if (messageContainsAll(e, "Access denied for user", "using password")) {
                return TestConnectDbResult.USERNAME_OR_PASSWORD_ERROR;
            } else {
                if (e.getCause() instanceof IOException) {
                    return TestConnectDbResult.CREATE_CONNECT_ERROR;
                }
                return TestConnectDbResult.SQL_EXCEPTION_UNKNOWN;
            }
        } catch (Exception e) {
            InstallLogUtil.logFailure(LOGGER, Level.SEVERE,
                    InstallLogUtil.FailurePhase.DATABASE_CONNECTION_TEST, e);
        }
        return TestConnectDbResult.UNKNOWN;
    }

    static boolean messageContainsAll(SQLException exception, String... fragments) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        for (String fragment : fragments) {
            if (!message.contains(fragment)) {
                return false;
            }
        }
        return true;
    }

    private boolean createDatabase() throws Exception {
        String dbName = dbConn.getDbName();
        String dbHost = dbConn.getDbHost();
        String dbPort = dbConn.getDbPort();
        String dbType = dbConn.getDbType();
        if (StringUtils.isEmpty(dbName) || StringUtils.isEmpty(dbHost) || StringUtils.isEmpty(dbPort)) return false;

        Properties properties = new Properties();
        properties.putAll(dbConn.toMap());
        String baseJdbcUrl = "jdbc:" + dbType + "://" + dbHost + ":" + dbPort + "/";
        String jdbcUrlQueryStr = installConfig.getJdbcUrlQueryStr(dbType, Collections.emptyMap());
        properties.put("jdbcUrl", baseJdbcUrl + (StringUtils.isEmpty(jdbcUrlQueryStr) ? "" : "?" + jdbcUrlQueryStr));

        try (DataSourceWrapperImpl ds = buildDataSource(properties, EnvKit.isDevMode())) {
             DAO dao = new DAO(ds);
             return dao.execute("CREATE DATABASE IF NOT EXISTS `" + dbName + "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }


    /**
     * 保存程序的数据库链接信息
     *
     * @throws IOException
     */
    private void installSuccess(InstallDatabaseConfig databaseConfig) throws IOException {
        try (InstallStateStore.InstallStateTransaction installState = installStateStore.prepare(
                installConfig.getDbPropertiesFile(), databaseConfig.toMap(), installAction.getLockFile())) {
            installState.commitForCallback();
            if (installConfig.isMissingConfig()) {
                LOGGER.info("Need config, skip call action.installSuccess()");
            }
            installAction.installSuccess();
            installState.complete();
        }
    }

    private boolean startInstall(InstallDatabaseConfig dbConn, InstallSiteConfig blogMsg) {
        String currentStep = "preflight";
        Properties properties = dbConn.toProperties();
        //
        try {
            emitRunning(currentStep);
            new InstallPreflightService().assertReady(installConfig);
            InstallOperationLock operationLock = InstallOperationLock.tryAcquire(installAction.getLockFile());
            if (operationLock == null) {
                throw new InstallOperationInProgressException("INSTALL");
            }
            try (InstallOperationLock ignored = operationLock) {
                if (installAction.isInstalled()) {
                    throw new InstalledException();
                }
                emitComplete(currentStep);

                currentStep = "database";
                emitRunning(currentStep);
                LocalSqliteSupport.DatabaseReservation sqliteReservation = null;
                if (dbConn.isLocalSqlite()) {
                    try {
                        sqliteReservation = LocalSqliteSupport.reserveDatabaseFile(installConfig);
                    } catch (FileAlreadyExistsException e) {
                        throw new InstallException(TestConnectDbResult.DATABASE_NOT_EMPTY);
                    }
                }
                try (LocalSqliteSupport.DatabaseReservation reservation = sqliteReservation;
                     DataSourceWrapperImpl ds = buildDataSource(properties, EnvKit.isDevMode())) {
                    ds.testConnection();
                    emitComplete(currentStep);

                    DAO dao = new DAO(ds);
                    if (InstallSchemaSafety.containsInstallTable(ds, dbConn)) {
                        throw new InstallException(TestConnectDbResult.DATABASE_NOT_EMPTY);
                    }
                    String sql = IOUtil.getStringInputStream(
                            InstallService.class.getResourceAsStream("/init-table-structure.sql"));
                    List<String> sqlList = prepareInstallSql(sql, ds.isWebApi(), dbConn);
                    currentStep = "schema";
                    emitRunning(currentStep);
                    if (reservation != null) {
                        reservation.preserve();
                    }
                    for (String sqlSt : sqlList) {
                        if (InstallSchemaSafety.isDropStatement(sqlSt)) {
                            continue;
                        }
                        dao.execute(sqlSt);
                    }
                    emitComplete(currentStep);

                    currentStep = "seed-website";
                    emitRunning(currentStep);
                    List<Boolean> results = new ArrayList<>();
                    boolean websiteResult = initWebSite(dao);
                    results.add(websiteResult);
                    String failedStep = websiteResult ? null : currentStep;
                    if (websiteResult) {
                        emitComplete(currentStep);
                    }

                    currentStep = "seed-admin";
                    emitRunning(currentStep);
                    boolean adminResult = initUser(blogMsg, dao);
                    results.add(adminResult);
                    if (!adminResult && failedStep == null) {
                        failedStep = currentStep;
                    }
                    if (adminResult) {
                        emitComplete(currentStep);
                    }

                    currentStep = "seed-defaults";
                    emitRunning(currentStep);
                    List<Boolean> defaultResults = new ArrayList<>();
                    defaultResults.add(insertNav(dao));
                    defaultResults.add(initPlugin(dao));
                    defaultResults.add(insertType(dao));
                    defaultResults.add(insertTag(dao));
                    defaultResults.add(insertFirstArticle(dao));
                    results.addAll(defaultResults);
                    boolean defaultsResult = defaultResults.stream().allMatch(e -> Objects.equals(e, true));
                    if (!defaultsResult && failedStep == null) {
                        failedStep = currentStep;
                    }
                    if (defaultsResult) {
                        emitComplete(currentStep);
                    }

                    if (!results.stream().allMatch(e -> Objects.equals(e, true))) {
                        currentStep = Objects.requireNonNullElse(failedStep, "install");
                        emitError(currentStep, new IllegalStateException("Install step failed: " + currentStep));
                        return false;
                    }

                    currentStep = "config";
                    emitRunning(currentStep);
                    installRecoveryStore.save(installAction.getLockFile(), dbConn);
                    installSuccess(dbConn);
                    clearRecoveryState();
                    emitComplete(currentStep);
                }
            }
            return true;
        } catch (AbstractInstallException e) {
            LOGGER.log(Level.WARNING, "Installation rejected by schema safety checks");
            throw e;
        } catch (Exception e) {
            emitError(currentStep, e);
            InstallLogUtil.logFailure(LOGGER, Level.SEVERE, InstallLogUtil.FailurePhase.INSTALL, e);
        }
        return false;
    }

    private boolean resumeInstall() {
        String currentStep = "preflight";
        try {
            emitRunning(currentStep);
            new InstallPreflightService().assertReady(installConfig);
            InstallOperationLock operationLock = InstallOperationLock.tryAcquire(installAction.getLockFile());
            if (operationLock == null) {
                throw new InstallOperationInProgressException("INSTALL");
            }
            try (InstallOperationLock ignored = operationLock) {
                if (installAction.isInstalled()) {
                    throw new InstalledException();
                }
                Optional<InstallDatabaseConfig> recoveryConfig =
                        installRecoveryStore.load(installAction.getLockFile());
                if (recoveryConfig.isEmpty()) {
                    return false;
                }
                emitComplete(currentStep);

                currentStep = "database";
                emitRunning(currentStep);
                InstallDatabaseConfig recoveredDatabase = recoveryConfig.get();
                try (DataSourceWrapperImpl dataSource = buildDataSource(
                        recoveredDatabase.toProperties(), EnvKit.isDevMode())) {
                    dataSource.testConnection();
                    if (!InstallSchemaSafety.containsCompleteInstallState(dataSource, recoveredDatabase)) {
                        return false;
                    }
                }
                emitComplete(currentStep);

                currentStep = "config";
                emitRunning(currentStep);
                installSuccess(recoveredDatabase);
                clearRecoveryState();
                emitComplete(currentStep);
                return true;
            }
        } catch (AbstractInstallException e) {
            LOGGER.log(Level.WARNING, "Installation recovery request was rejected");
            throw e;
        } catch (Exception e) {
            emitError(currentStep, e);
            InstallLogUtil.logFailure(LOGGER, Level.SEVERE,
                    InstallLogUtil.FailurePhase.INSTALL_RECOVERY, e);
            return false;
        }
    }

    private void clearRecoveryState() {
        try {
            installRecoveryStore.clear(installAction.getLockFile());
        } catch (IOException e) {
            InstallLogUtil.logFailure(LOGGER, Level.WARNING,
                    InstallLogUtil.FailurePhase.RECOVERY_STATE_CLEANUP, e);
        }
    }

    private void emitRunning(String code) {
        emitProgress(InstallProgressEvent.running(code));
    }

    private void emitComplete(String code) {
        emitProgress(InstallProgressEvent.complete(code));
    }

    private void emitError(String code, Exception e) {
        emitProgress(InstallProgressEvent.error(code,
                InstallI18nUtil.getInstallStringFromRes("streamFailed")));
    }

    private void emitProgress(InstallProgressEvent event) {
        try {
            progressListener.onProgress(event);
        } catch (Exception listenerFailure) {
            InstallLogUtil.logFailure(LOGGER, Level.FINE,
                    InstallLogUtil.FailurePhase.PROGRESS_DELIVERY, listenerFailure);
        }
    }

    static String getPlainSearchText(String content) {
        if (StringUtils.isEmpty(content)) {
            return "";
        }
        return Jsoup.parse(content).body().text();
    }

    static List<String> prepareInstallSql(String sql, boolean webApi, InstallDatabaseConfig dbConn) {
        if (webApi || dbConn.isLocalSqlite()) {
            return SqlConvertUtils.doMySQLToSqliteBySqlText(sql);
        }
        if (shouldNormalizeInstallSqlForH2(dbConn)) {
            return SqlConvertUtils.doMySQLToH2BySqlText(sql);
        }
        return SqlConvertUtils.extractExecutableSql(sql);
    }

    private static boolean shouldNormalizeInstallSqlForH2(InstallDatabaseConfig dbConn) {
        return "h2".equalsIgnoreCase(dbConn.getDbType())
                || "org.h2.Driver".equals(dbConn.getDriverClass());
    }

    boolean insertFirstArticle(DAO dao) throws Exception {
        int logId = 1;
        String insetLog = "INSERT INTO `log`(`logId`,`canComment`,`keywords`,`alias`,`typeId`,`userId`,`title`,`content`,`plain_content`,`markdown`,`digest`,`releaseTime`,`last_update_date`,`rubbish`,`privacy`) VALUES (" + logId + ",?,?,?,1,1,?,?,?,?,?,?,?,?,?)";
        List<Object> params = new ArrayList<>();
        try (InputStream in = InstallService.class.getResourceAsStream("/i18n/init-blog/" + installConfig.getAcceptLanguage() + ".md")) {
            InitialArticleTemplateData data = new InitialArticleTemplateData(contextPath + "/admin/article-edit?id=" + logId);
            String markdown = new BasicTemplateRender(data.toTemplateMap(), InstallService.class).render(in);
            //html read
            try (InputStream htmlIn = InstallService.class.getResourceAsStream("/i18n/init-blog/" + installConfig.getAcceptLanguage() + ".html")) {
                String content = new BasicTemplateRender(data.toTemplateMap(), InstallService.class).render(htmlIn);
                params.add(true);
                params.add(InstallI18nUtil.getInstallStringFromRes("defaultType"));
                params.add("hello-world");
                params.add(InstallI18nUtil.getInstallStringFromRes("helloWorld"));
                params.add(content);
                params.add(getPlainSearchText(content));
                params.add(markdown);
                params.add(content);
                String installDate = configMsg.getInstallDate();
                if (StringUtils.isEmpty(installDate)) {
                    params.add(new Date());
                    params.add(new Date());
                } else {
                    Date parsedDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z").parse(installDate);
                    params.add(parsedDate);
                    params.add(parsedDate);
                }

                params.add(false);
                params.add(false);
            }
        }
        return dao.execute(insetLog, params.toArray());
    }

    boolean insertType(DAO dao) throws SQLException {
        String insertLogType = "INSERT INTO `type`(`typeId`, `typeName`, `remark`, `alias`) VALUES (1,'" + InstallI18nUtil.getInstallStringFromRes("defaultType") + "','','note')";
        return dao.execute(insertLogType);
    }

    boolean insertTag(DAO dao) throws SQLException {
        String insertTag = "INSERT INTO `tag`(`tagId`,`text`,`count`) VALUES (1,'" + InstallI18nUtil.getInstallStringFromRes("defaultType") + "',1)";
        return dao.execute(insertTag);
    }

    boolean initPlugin(DAO dao) throws SQLException {
        String insertPluginSql = "INSERT INTO `plugin` VALUES (1,NULL,true,'" + InstallI18nUtil.getInstallStringFromRes("category") + "',NULL,'types',3)," +
                "(2,NULL,true,'" + InstallI18nUtil.getInstallStringFromRes("tag") + "',NULL,'tags',3)," +
                "(3,NULL,true,'" + InstallI18nUtil.getInstallStringFromRes("link") + "',NULL,'links',2)," +
                "(4,NULL,true,'" + InstallI18nUtil.getInstallStringFromRes("archive") + "',NULL,'archives',3)";
        return dao.execute(insertPluginSql);
    }

    boolean insertNav(DAO dao) throws SQLException {
        String insertLogNavSql = "INSERT INTO `lognav`( `navId`,`url`, `navName`,`icon`, `sort`) VALUES (?,?,?,?,?)";
        return dao.execute(insertLogNavSql, 1, "/", InstallI18nUtil.getInstallStringFromRes("home"), "iconfont icon-home-fill", 1)
                && dao.execute(insertLogNavSql, 2, "/admin/login", InstallI18nUtil.getInstallStringFromRes("manage"), "iconfont icon-user-fill", 2);
    }

    boolean initUser(InstallSiteConfig blogMsg, DAO dao) throws SQLException {
        String insertUserSql = "INSERT INTO `user`( `userId`,`userName`, `password`, `email`,`secretKey`) VALUES (1,?,?,?,?)";
        return dao.execute(insertUserSql, blogMsg.getUsername(), installConfig.encryptPassword(blogMsg.getPassword()),
                configMsg.getEmail(), configMsg.secretKeyOrNew());
    }

    boolean initWebSite(DAO dao) throws SQLException {
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO `website` (`name`, `value`) VALUES ");
        Map<String, Object> defaultMap = getDefaultWebSiteSettings(configMsg).toMap();
        for (int i = 0; i < defaultMap.size(); i++) {
            sb.append("(").append("?").append(",").append("?").append("),");
        }
        List<Object> params = new ArrayList<>();
        for (Map.Entry<String, Object> e : defaultMap.entrySet()) {
            params.add(e.getKey());
            params.add(e.getValue());
        }
        String insertWebSql = sb.substring(0, sb.toString().length() - 1);
        return dao.execute(insertWebSql, params.toArray());
    }

    private static class InitialArticleTemplateData {

        private final String editUrl;

        private InitialArticleTemplateData(String editUrl) {
            this.editUrl = editUrl;
        }

        Map<String, Object> toTemplateMap() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("editUrl", editUrl);
            return data;
        }
    }
}
