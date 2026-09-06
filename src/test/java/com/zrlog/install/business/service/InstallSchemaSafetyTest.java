package com.zrlog.install.business.service;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InstallSchemaSafetyTest {

    @Test
    public void shouldRecognizeEveryDropTableStatementFormUsedByInstallSql() {
        assertTrue(InstallSchemaSafety.isDropStatement("DROP TABLE IF EXISTS `log`"));
        assertTrue(InstallSchemaSafety.isDropStatement("drop table if exists `log`, `comment`"));
        assertTrue(InstallSchemaSafety.isDropStatement("  DrOp TABLE `website`"));
        assertTrue(InstallSchemaSafety.isDropStatement("/* schema header */ DROP TABLE `user`"));
        assertTrue(InstallSchemaSafety.isDropStatement("-- schema header\nDROP TABLE `user`"));
        assertTrue(InstallSchemaSafety.isDropStatement("# schema header\nDROP/**/TABLE `user`"));
    }

    @Test
    public void shouldNotTreatNondestructiveStatementsOrStringValuesAsDropStatements() {
        assertFalse(InstallSchemaSafety.isDropStatement("CREATE TABLE `log` (`id` int)"));
        assertFalse(InstallSchemaSafety.isDropStatement("SELECT 'DROP TABLE user'"));
        assertFalse(InstallSchemaSafety.isDropStatement(null));
    }
}
