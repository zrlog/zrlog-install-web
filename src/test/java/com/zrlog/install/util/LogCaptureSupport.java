package com.zrlog.install.util;

import com.hibegin.common.util.LoggerUtil;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class LogCaptureSupport extends Handler implements AutoCloseable {

    private final Logger logger;
    private final Level previousLevel;
    private final List<LogRecord> records = new CopyOnWriteArrayList<>();

    private LogCaptureSupport(Class<?> loggerClass) {
        logger = LoggerUtil.getLogger(loggerClass);
        previousLevel = logger.getLevel();
        setLevel(Level.ALL);
        logger.setLevel(Level.ALL);
        logger.addHandler(this);
    }

    public static LogCaptureSupport capture(Class<?> loggerClass) {
        return new LogCaptureSupport(loggerClass);
    }

    @Override
    public void publish(LogRecord record) {
        records.add(record);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
        logger.removeHandler(this);
        logger.setLevel(previousLevel);
    }

    public boolean hasThrown() {
        return records.stream().anyMatch(record -> record.getThrown() != null);
    }

    public String text() {
        StringBuilder text = new StringBuilder();
        for (LogRecord record : records) {
            text.append(record.getMessage()).append('\n');
            if (record.getParameters() != null) {
                for (Object parameter : record.getParameters()) {
                    text.append(String.valueOf(parameter)).append('\n');
                }
            }
            appendThrowable(text, record.getThrown());
        }
        return text.toString();
    }

    private static void appendThrowable(StringBuilder text, Throwable throwable) {
        if (throwable == null) {
            return;
        }
        text.append(throwable).append('\n');
        appendThrowable(text, throwable.getCause());
    }
}
