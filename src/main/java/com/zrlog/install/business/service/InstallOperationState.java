package com.zrlog.install.business.service;

import java.util.concurrent.atomic.AtomicReference;

/** Coordinates requests belonging to one installer; file locks also protect shared directories. */
public final class InstallOperationState {

    public enum Operation { INSTALL, UPGRADE }

    private final AtomicReference<Operation> running = new AtomicReference<>();

    public Operation tryAcquire(Operation requested) {
        while (true) {
            Operation active = running.get();
            if (active != null) {
                return active;
            }
            if (running.compareAndSet(null, requested)) {
                return null;
            }
        }
    }

    public void release(Operation operation) {
        running.compareAndSet(operation, null);
    }
}
