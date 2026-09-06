package com.zrlog.install.web;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.install.exception.InvalidInstallRequestException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class InstallRequestSecurity {

    public static final String TOKEN_HEADER = "X-ZrLog-Install-Token";
    private static final String TOKEN_ENVIRONMENT_VARIABLE = "ZRLOG_INSTALL_TOKEN";

    private InstallRequestSecurity() {
    }

    private static String getSetupToken() {
        return resolveSetupToken(System.getenv(TOKEN_ENVIRONMENT_VARIABLE));
    }

    public static boolean isTokenRequired() {
        return !getSetupToken().isEmpty();
    }

    static String resolveSetupToken(String environmentToken) {
        return normalizeToken(environmentToken);
    }

    private static String normalizeToken(String token) {
        return token == null ? "" : token.trim();
    }

    public static void assertMutationRequest(HttpRequest request) {
        assertMutationRequest(request, getSetupToken());
    }

    static void assertMutationRequest(HttpRequest request, String setupToken) {
        String contentType = request.getHeader("Content-Type");
        String query = request.getQueryStr();
        String normalizedSetupToken = normalizeToken(setupToken);
        if (!HttpMethod.POST.equals(request.getMethod())
                || !isJsonContentType(contentType)
                || (request.getParamMap() != null && !request.getParamMap().isEmpty())
                || (query != null && !query.trim().isEmpty())
                || (!normalizedSetupToken.isEmpty()
                    && !isValidToken(request.getHeader(TOKEN_HEADER), normalizedSetupToken))) {
            throw new InvalidInstallRequestException("Invalid installation request");
        }
    }

    public static boolean isJsonContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String mediaType = contentType.split(";", 2)[0].trim();
        return "application/json".equalsIgnoreCase(mediaType);
    }

    private static boolean isValidToken(String requestToken, String setupToken) {
        if (requestToken == null) {
            return false;
        }
        return MessageDigest.isEqual(
                setupToken.getBytes(StandardCharsets.UTF_8),
                requestToken.getBytes(StandardCharsets.UTF_8));
    }
}
