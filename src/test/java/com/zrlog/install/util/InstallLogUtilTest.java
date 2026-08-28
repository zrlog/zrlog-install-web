package com.zrlog.install.util;

import org.junit.Test;

import java.util.logging.Level;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InstallLogUtilTest {

    @Test
    public void shouldOnlyLogStablePhaseAndExceptionClass() {
        String sensitiveMessage = "/private/recovery/do-not-expose.properties "
                + "jdbc:mysql://db.internal/zrlog?password=do-not-expose "
                + "SELECT password FROM user";

        try (LogCaptureSupport logs = LogCaptureSupport.capture(InstallLogUtilTest.class)) {
            InstallLogUtil.logFailure(
                    com.hibegin.common.util.LoggerUtil.getLogger(InstallLogUtilTest.class),
                    Level.SEVERE,
                    InstallLogUtil.FailurePhase.RECOVERY_STATE_CLEANUP,
                    new IllegalStateException(sensitiveMessage));

            assertTrue(logs.text().contains("phase=recovery-state-cleanup"));
            assertTrue(logs.text().contains("exception=java.lang.IllegalStateException"));
            assertFalse(logs.text().contains("do-not-expose"));
            assertFalse(logs.text().contains("jdbc:mysql"));
            assertFalse(logs.text().contains("SELECT password"));
            assertFalse(logs.hasThrown());
        }
    }
}
