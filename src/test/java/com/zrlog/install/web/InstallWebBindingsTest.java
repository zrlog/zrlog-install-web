package com.zrlog.install.web;

import com.google.gson.Gson;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.web.MethodInterceptor;
import com.zrlog.install.business.response.InstallResourceResponse;
import com.zrlog.install.business.response.InstallRuntimeResourceResponse;
import com.zrlog.install.business.response.InstallUpgradeResult;
import com.zrlog.install.business.service.InstallUpgradeAction;
import com.zrlog.install.exception.InvalidInstallRequestException;
import com.zrlog.install.util.InstallI18nUtil;
import com.zrlog.install.web.config.DefaultInstallAction;
import com.zrlog.install.web.config.DefaultInstallConfig;
import com.zrlog.install.web.config.InstallWebBindings;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class InstallWebBindingsTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void routedResourcesAndValidationFollowTheirOwningApplicationAndCurrentRequest() throws Exception {
        ThreadLocal<String> language = ThreadLocal.withInitial(() -> "zh_CN");
        Site first = new Site(temporary.newFolder("first").toPath(), language);
        Site second = new Site(temporary.newFolder("second").toPath(), ThreadLocal.withInitial(() -> "en_US"));
        ServerConfig firstServer = server(first);
        ServerConfig secondServer = server(second);
        assertEquals("zh_CN", resources(firstServer).getLang());
        assertEquals("en_US", resources(secondServer).getLang());
        language.set("en_US");
        assertEquals("en_US", resources(firstServer).getLang());
        language.set("zh_CN");
        Throwable error = assertThrows(InvocationTargetException.class,
                () -> dispatch(firstServer, "startInstall", Map.of("dbType", "sqlite"), false)).getCause();
        assertTrue(error instanceof InvalidInstallRequestException);
        assertEquals(InstallI18nUtil.getInstallStringFromRes("validationAdminUsername", "zh_CN"), error.getMessage());
        assertFalse(first.getDbPropertiesFile().exists());
        assertFalse(second.getDbPropertiesFile().exists());
        language.remove();
    }

    @Test
    public void separateApplicationsInstallIndependentlyWhileSharedDirectoryAndApplicationStayLocked() throws Exception {
        CountDownLatch upgrading = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        Site first = new Site(temporary.newFolder("upgrading").toPath(), ThreadLocal.withInitial(() -> "zh_CN")) {
            @Override public InstallUpgradeAction getUpgradeAction() {
                return new InstallUpgradeAction() {
                    @Override public boolean isSupported() { return true; }
                    @Override public InstallUpgradeResult upgrade(ProgressListener listener) throws Exception {
                        upgrading.countDown();
                        if (!finish.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Upgrade timed out");
                        return new InstallUpgradeResult(true, "done");
                    }
                };
            }
        };
        ThreadLocal<String> language = ThreadLocal.withInitial(() -> "zh_CN");
        Site second = new Site(temporary.newFolder("installing").toPath(), language);
        ServerConfig firstServer = server(first);
        ServerConfig secondServer = server(second);
        // A distinct container sharing the same directory must still respect the persistent lock.
        ServerConfig sameDirectory = server(new Site(first.root, ThreadLocal.withInitial(() -> "zh_CN")));
        InstallWebLayerTest.CapturedResponse upgrade = dispatch(firstServer, "startUpgrade", Map.of(), true);
        try {
            assertTrue(upgrading.await(5, TimeUnit.SECONDS));
            assertTrue(stream(dispatch(firstServer, "startInstall", installBody(), true)).contains("UPGRADE_IN_PROGRESS"));
            assertTrue(stream(dispatch(sameDirectory, "startInstall", installBody(), true)).contains("INSTALL_IN_PROGRESS"));
            language.set("en_US");
            String result = stream(dispatch(secondServer, "startInstall", installBody(), true));
            assertTrue(result, result.contains("event: install-complete"));
            assertFalse(result, result.contains("event: install-error"));
            assertTrue(second.getDbPropertiesFile().isFile());
            assertTrue(second.getAction().getLockFile().isFile());
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + second.root.resolve("zrlog.db"));
                 var query = connection.createStatement();
                 var rows = query.executeQuery("select value from website where name='language'")) {
                assertTrue(rows.next());
                // The SSE worker has no request ThreadLocal; its data must use the captured locale.
                assertEquals("en_US", rows.getString(1));
            }
            assertFalse(first.getDbPropertiesFile().exists());
        } finally {
            finish.countDown();
            assertTrue(stream(upgrade).contains("event: upgrade-complete"));
            language.remove();
        }
        // A subsequent request proves the per-application guard and file lock were both released.
        assertTrue(stream(dispatch(firstServer, "startInstall", installBody(), true)).contains("event: install-complete"));
    }

    private static ServerConfig server(Site site) {
        ServerConfig server = new ServerConfig();
        server.setContextPath("/blog");
        InstallWebBindings.configure(server, site);
        return server;
    }

    private static InstallRuntimeResourceResponse resources(ServerConfig server) throws Exception {
        return (InstallRuntimeResourceResponse) ((InstallResourceResponse)
                dispatch(server, "installResource", null, false).json).getData();
    }

    private static Map<String, String> installBody() {
        return Map.of("dbType", "sqlite", "title", "Injected site", "username", "admin",
                "password", "test-password", "email", "admin@example.com");
    }

    private static String stream(InstallWebLayerTest.CapturedResponse response) throws Exception {
        try (var input = response.written) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static InstallWebLayerTest.CapturedResponse dispatch(ServerConfig server, String action,
                                                                Map<String, String> body, boolean sse) throws Exception {
        String uri = "/api/install/" + action;
        Map<String, String> headers = Map.of("Content-Type", "application/json", "Accept", sse ? "text/event-stream" : "application/json");
        HttpRequest delegate = InstallWebLayerTest.request(uri, Map.of(), headers);
        RequestConfig requestConfig = new RequestConfig();
        requestConfig.setRouter(server.getRouter());
        HttpRequest request = (HttpRequest) Proxy.newProxyInstance(InstallWebBindingsTest.class.getClassLoader(),
                new Class[]{HttpRequest.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getServerConfig": return server;
                        case "getRequestConfig": return requestConfig;
                        case "getMethod": return body == null ? HttpMethod.GET : HttpMethod.POST;
                        case "getRequestBodyByteBuffer": return ByteBuffer.wrap(new Gson().toJson(body).getBytes(StandardCharsets.UTF_8));
                        default: return method.invoke(delegate, args);
                    }
                });
        InstallWebLayerTest.CapturedResponse response = new InstallWebLayerTest.CapturedResponse();
        new MethodInterceptor().doInterceptor(request, response.response());
        return response;
    }

    private static class Site extends DefaultInstallConfig {
        private final Path root;
        private final ThreadLocal<String> language;
        private Site(Path root, ThreadLocal<String> language) { this.root = root; this.language = language; }
        @Override public String getAcceptLanguage() { return language.get(); }
        @Override public File getDbPropertiesFile() { return root.resolve("db.properties").toFile(); }
        @Override public InstallAction getAction() {
            return new DefaultInstallAction() {
                @Override public File getLockFile() { return root.resolve("install.lock").toFile(); }
            };
        }
    }
}
