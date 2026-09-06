package com.zrlog.install.util;

import com.zrlog.install.exception.AbstractInstallException;
import com.zrlog.install.exception.InstallException;
import com.zrlog.install.exception.InstallOperationInProgressException;
import com.zrlog.install.exception.InstallRecoveryUnavailableException;
import com.zrlog.install.exception.InstalledException;
import com.zrlog.install.exception.InvalidInstallRequestException;
import com.zrlog.install.exception.MissingDbHostException;
import com.zrlog.install.exception.MissingDbNameException;
import com.zrlog.install.exception.MissingDbPortException;
import com.zrlog.install.exception.MissingDbUserNameException;

import java.util.List;
import java.util.Set;

public final class InstallErrorResponsePolicy {

    private static final Set<Class<?>> CONTROLLED_TYPES = Set.of(
            InstallException.class,
            InstallOperationInProgressException.class,
            InstallRecoveryUnavailableException.class,
            InstalledException.class,
            MissingDbHostException.class,
            MissingDbNameException.class,
            MissingDbPortException.class,
            MissingDbUserNameException.class);
    private static final Set<String> CONTROLLED_INVALID_MESSAGES = Set.of(
            "Invalid installation request",
            "JSON request body must be an object",
            "JSON request field must be a primitive value",
            "Invalid JSON request body");
    private static final List<String> CONTROLLED_INVALID_MESSAGE_KEYS = List.of(
            "validationDbType",
            "validationDbHost",
            "validationDbPort",
            "validationDbUserName",
            "validationDbPassword",
            "validationDbName",
            "validationAdminUsername",
            "validationAdminPassword",
            "validationSiteTitle",
            "validationSiteSubtitle",
            "validationAdminEmail",
            "installCompletionUnavailable");

    private InstallErrorResponsePolicy() {
    }

    public static boolean isControlled(AbstractInstallException exception) {
        if (exception == null) {
            return false;
        }
        if (CONTROLLED_TYPES.contains(exception.getClass())) {
            return true;
        }
        return exception.getClass() == InvalidInstallRequestException.class
                && isControlledInvalidMessage(exception.getMessage());
    }

    public static String controlledMessage(AbstractInstallException exception) {
        if (!isControlled(exception)) {
            throw new IllegalArgumentException("Uncontrolled install exception response");
        }
        String message = exception.getMessage();
        return message == null ? "" : message;
    }

    private static boolean isControlledInvalidMessage(String message) {
        if (message == null) {
            return false;
        }
        if (CONTROLLED_INVALID_MESSAGES.contains(message)) {
            return true;
        }
        for (String resourceKey : CONTROLLED_INVALID_MESSAGE_KEYS) {
            String controlledMessage = InstallI18nUtil.getInstallStringFromRes(resourceKey);
            if (!controlledMessage.isEmpty() && controlledMessage.equals(message)) {
                return true;
            }
        }
        return false;
    }
}
