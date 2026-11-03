package com.zrlog.install.business.response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class InstallProgressEventTest {

    @Test
    public void shouldBuildProgressEventsWithFactoryMethods() {
        InstallProgressEvent running = InstallProgressEvent.running("connectDb");
        InstallProgressEvent complete = InstallProgressEvent.complete("createTable");
        InstallProgressEvent error = InstallProgressEvent.error("initData", "missing table");

        assertEquals("connectDb", running.getCode());
        assertEquals("running", running.getStatus());
        assertNull(running.getDetail());
        assertEquals("createTable", complete.getCode());
        assertEquals("complete", complete.getStatus());
        assertEquals("initData", error.getCode());
        assertEquals("error", error.getStatus());
        assertEquals("missing table", error.getDetail());
    }

    @Test
    public void shouldUpdateProgressEventFields() {
        InstallProgressEvent event = new InstallProgressEvent();

        event.setCode("download");
        event.setStatus("running");
        event.setDetail("fetching release");

        assertEquals("download", event.getCode());
        assertEquals("running", event.getStatus());
        assertEquals("fetching release", event.getDetail());
    }
}
