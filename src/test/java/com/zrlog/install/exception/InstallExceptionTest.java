package com.zrlog.install.exception;

import com.zrlog.install.business.type.TestConnectDbResult;
import com.zrlog.install.util.InstallErrorResponsePolicy;
import com.zrlog.install.web.InstallConstants;
import com.zrlog.install.web.config.DefaultInstallConfig;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InstallExceptionTest {

    @After
    public void tearDown() {
        InstallConstants.installConfig = new DefaultInstallConfig();
    }

    @Test
    public void shouldExposeDatabaseErrorCodeAndMessage() {
        InstallConstants.installConfig = new DefaultInstallConfig();
        InstallException exception = new InstallException(TestConnectDbResult.DB_NOT_EXISTS);

        assertEquals(9000, exception.getError());
        assertEquals("DB_NOT_EXISTS", exception.getCode());
        assertTrue(exception.getMessage().contains("[Error-DB_NOT_EXISTS]"));
        assertTrue(exception.getMessage().length() > "[Error-DB_NOT_EXISTS] - ".length());
    }

    @Test
    public void shouldExposeInstalledErrorCodeAndMessage() {
        InstallConstants.installConfig = new DefaultInstallConfig();
        InstalledException exception = new InstalledException();

        assertEquals(9020, exception.getError());
        assertEquals("INSTALL_ALREADY_COMPLETED", exception.getCode());
        assertTrue(exception.getMessage().length() > 0);
    }

    @Test
    public void shouldUseWarInstalledMessageWhenWarModeIsEnabled() {
        InstallConstants.installConfig = new DefaultInstallConfig() {
            @Override
            public boolean isWarMode() {
                return true;
            }
        };
        InstalledException exception = new InstalledException();

        assertEquals(9020, exception.getError());
        assertTrue(exception.getMessage().length() > 0);
    }

    @Test
    public void shouldExposeMissingFieldErrors() {
        assertError(new MissingDbHostException(), 9021);
        assertError(new MissingDbPortException(), 9022);
        assertError(new MissingDbNameException(), 9023);
        assertError(new MissingDbUserNameException(), 9023);
    }

    @Test
    public void shouldExposeInstallRequestStateErrors() {
        InstallOperationInProgressException inProgress =
                new InstallOperationInProgressException("INSTALL");
        InstallOperationInProgressException upgradeInProgress =
                new InstallOperationInProgressException("UPGRADE");
        InvalidInstallRequestException invalidRequest =
                new InvalidInstallRequestException("Invalid JSON request body");

        assertEquals(9024, inProgress.getError());
        assertEquals("INSTALL_IN_PROGRESS", inProgress.getCode());
        assertTrue(inProgress.getMessage().length() > 0);
        assertEquals("UPGRADE_IN_PROGRESS", upgradeInProgress.getCode());
        assertTrue(upgradeInProgress.getMessage().contains("upgrade"));
        assertEquals(9025, invalidRequest.getError());
        assertEquals("INVALID_INSTALL_REQUEST", invalidRequest.getCode());

        InstallRecoveryUnavailableException recoveryUnavailable =
                new InstallRecoveryUnavailableException();
        assertEquals(9026, recoveryUnavailable.getError());
        assertEquals("INSTALL_RECOVERY_UNAVAILABLE", recoveryUnavailable.getCode());
        assertEquals("Invalid JSON request body", invalidRequest.getMessage());
    }

    @Test
    public void shouldOnlyTrustBuiltInResponseMessages() {
        InvalidInstallRequestException controlled =
                new InvalidInstallRequestException("Invalid JSON request body");
        InvalidInstallRequestException uncontrolled =
                new InvalidInstallRequestException("password=do-not-expose");
        AbstractInstallException custom = new AbstractInstallException() {
            @Override
            public int getError() {
                return 7777;
            }

            @Override
            public String getMessage() {
                return "jdbc:mysql://db/zrlog?password=do-not-expose";
            }
        };

        assertTrue(InstallErrorResponsePolicy.isControlled(controlled));
        assertTrue(InstallErrorResponsePolicy.isControlled(new MissingDbHostException()));
        assertTrue(InstallErrorResponsePolicy.isControlled(new MissingDbPortException()));
        assertTrue(InstallErrorResponsePolicy.isControlled(new MissingDbNameException()));
        assertTrue(InstallErrorResponsePolicy.isControlled(new MissingDbUserNameException()));
        assertFalse(InstallErrorResponsePolicy.isControlled(uncontrolled));
        assertFalse(InstallErrorResponsePolicy.isControlled(custom));
    }

    private static void assertError(AbstractInstallException exception, int error) {
        assertEquals(error, exception.getError());
        assertTrue(exception.getMessage().length() > 0);
    }
}
