package com.zrlog.install.util;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class InstallLogUtil {

    private InstallLogUtil() {
    }

    public static void logFailure(Logger logger, Level level, FailurePhase phase, Throwable failure) {
        String exceptionClass = failure == null ? "unknown" : failure.getClass().getName();
        logger.log(level, "Install operation failed [phase=" + phase.value + ", exception="
                + exceptionClass + "]");
    }

    public enum FailurePhase {
        LOCAL_SQLITE_PREPARATION("local-sqlite-preparation"),
        DATABASE_CONNECTION_TEST("database-connection-test"),
        DATABASE_CREATION("database-creation"),
        INSTALL("install"),
        INSTALL_RECOVERY("install-recovery"),
        RECOVERY_STATE_CLEANUP("recovery-state-cleanup"),
        PROGRESS_DELIVERY("progress-delivery"),
        EVENT_STREAM("event-stream"),
        INSTALL_REQUEST("install-request"),
        INSTALL_STATE_READ("install-state-read"),
        STALE_RECOVERY_STATE_CLEANUP("stale-recovery-state-cleanup"),
        INSTALL_STATE_CLEANUP("install-state-cleanup"),
        OPERATION_LOCK_RELEASE("operation-lock-release"),
        OPERATION_LOCK_CHANNEL_CLOSE("operation-lock-channel-close"),
        I18N_RESOURCE_LOAD("i18n-resource-load"),
        I18N_RESOURCE_CLOSE("i18n-resource-close");

        private final String value;

        FailurePhase(String value) {
            this.value = value;
        }
    }
}
