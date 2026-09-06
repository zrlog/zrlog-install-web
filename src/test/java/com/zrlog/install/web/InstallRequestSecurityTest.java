package com.zrlog.install.web;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.install.exception.InvalidInstallRequestException;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class InstallRequestSecurityTest {

    private static final String CONFIGURED_TOKEN = "configured-install-token";
    @Test
    public void shouldAllowJsonMutationWithoutTokenWhenTokenIsNotConfigured() {
        InstallRequestSecurity.assertMutationRequest(request(
                HttpMethod.POST, "application/json;charset=UTF-8", null, null, Collections.emptyMap()), "");
        InstallRequestSecurity.assertMutationRequest(request(
                HttpMethod.POST, "application/json", null, "ignored-token", Collections.emptyMap()), "");
    }

    @Test
    public void shouldAlwaysRejectNonJsonQueryOrFormMutations() {
        assertThrows(InvalidInstallRequestException.class, () ->
                InstallRequestSecurity.assertMutationRequest(request(
                        HttpMethod.GET, "application/json", null, null, Collections.emptyMap()), ""));
        assertThrows(InvalidInstallRequestException.class, () ->
                InstallRequestSecurity.assertMutationRequest(request(
                        HttpMethod.POST, "application/x-www-form-urlencoded", null, null,
                        Collections.emptyMap()), ""));
        assertThrows(InvalidInstallRequestException.class, () ->
                InstallRequestSecurity.assertMutationRequest(request(
                        HttpMethod.POST, "application/jsonp", null, null,
                        Collections.emptyMap()), ""));
        assertThrows(InvalidInstallRequestException.class, () ->
                InstallRequestSecurity.assertMutationRequest(request(
                        HttpMethod.POST, "application/json", "from=query", null,
                        Map.of("from", new String[]{"query"})), ""));
    }

    @Test
    public void shouldRequireConfiguredToken() {
        assertThrows(InvalidInstallRequestException.class, () ->
                InstallRequestSecurity.assertMutationRequest(request(
                        HttpMethod.POST, "application/json", null, null, Collections.emptyMap()),
                        CONFIGURED_TOKEN));
        assertThrows(InvalidInstallRequestException.class, () ->
                InstallRequestSecurity.assertMutationRequest(request(
                        HttpMethod.POST, "application/json", null, "wrong-token",
                        Collections.emptyMap()), CONFIGURED_TOKEN));

        InstallRequestSecurity.assertMutationRequest(request(
                HttpMethod.POST, "application/json", null, CONFIGURED_TOKEN, Collections.emptyMap()),
                CONFIGURED_TOKEN);
    }

    @Test
    public void shouldResolveOnlyExplicitNonBlankEnvironmentToken() {
        assertEquals("environment-token",
                InstallRequestSecurity.resolveSetupToken("  environment-token  "));
        assertEquals("", InstallRequestSecurity.resolveSetupToken("  "));
        assertEquals("", InstallRequestSecurity.resolveSetupToken(null));
    }

    private static HttpRequest request(HttpMethod method, String contentType, String query,
                                       String token, Map<String, String[]> paramMap) {
        return (HttpRequest) Proxy.newProxyInstance(
                InstallRequestSecurityTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, reflectedMethod, args) -> {
                    switch (reflectedMethod.getName()) {
                        case "getMethod":
                            return method;
                        case "getHeader":
                            return "Content-Type".equals(args[0]) ? contentType
                                    : InstallRequestSecurity.TOKEN_HEADER.equals(args[0]) ? token : null;
                        case "getQueryStr":
                            return query;
                        case "getParamMap":
                            return paramMap;
                        case "toString":
                            return "InstallSecurityHttpRequest";
                        default:
                            return null;
                    }
                });
    }
}
