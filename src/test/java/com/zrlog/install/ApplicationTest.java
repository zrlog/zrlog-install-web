package com.zrlog.install;

import org.junit.Test;

import static org.junit.Assert.assertNull;

public class ApplicationTest {

    @Test
    public void shouldSkipConfigInstallWhenConfigFileIsMissing() throws Exception {
        assertNull(Application.installFromConfigFile(new String[0], new com.zrlog.install.web.config.DefaultInstallConfig()));
        assertNull(Application.installFromConfigFile(new String[]{"/tmp/missing-zrlog-install-config.json"}, new com.zrlog.install.web.config.DefaultInstallConfig()));
    }

}
