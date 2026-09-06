package com.zrlog.install.util;

import com.google.gson.Gson;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.install.business.response.InstallApiResponses;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class InstallSseEmitter {

    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = LoggerUtil.getLogger(InstallSseEmitter.class);
    private static final String FALLBACK_ERROR_MESSAGE =
            "The operation could not be completed. Retry, then check the runtime logs for the deployment if the " +
                    "problem continues. Logs do not contain the setup passcode.";
    private final PipedOutputStream outputStream;

    public InstallSseEmitter(PipedOutputStream outputStream) {
        this.outputStream = outputStream;
    }

    public static void setHeaders(HttpResponse response) {
        response.getHeader().put("Content-Type", "text/event-stream;charset=UTF-8");
        response.addHeader("Cache-Control", "no-cache");
        response.addHeader("Connection", "keep-alive");
        response.addHeader("X-Accel-Buffering", "no");
    }

    public static void write(HttpResponse response, String threadName, String errorEvent, SseStreamWriter writer)
            throws IOException {
        setHeaders(response);
        PipedInputStream inputStream = new PipedInputStream();
        PipedOutputStream outputStream = new PipedOutputStream(inputStream);
        Thread streamThread = new Thread(() -> {
            InstallSseEmitter emitter = new InstallSseEmitter(outputStream);
            try {
                writer.write(emitter);
            } catch (Exception e) {
                emitter.sendError(errorEvent, e);
            } finally {
                emitter.close();
            }
        }, threadName);
        streamThread.start();
        response.write(inputStream);
    }

    public void send(String event, Object data) throws IOException {
        String payload = "event: " + event + "\n" + "data: " + GSON.toJson(data) + "\n\n";
        outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    public void sendError(String event, Exception e) {
        InstallLogUtil.logFailure(LOGGER, Level.SEVERE, InstallLogUtil.FailurePhase.EVENT_STREAM, e);
        try {
            String message = InstallI18nUtil.getInstallStringFromRes("streamFailed");
            send(event, InstallApiResponses.streamError(
                    message == null || message.isEmpty() ? FALLBACK_ERROR_MESSAGE : message));
        } catch (IOException ignored) {
            // Client connection may already be closed.
        }
    }

    private void close() {
        try {
            outputStream.close();
        } catch (IOException ignored) {
            // Client connection may already be closed.
        }
    }

    @FunctionalInterface
    public interface SseStreamWriter {

        void write(InstallSseEmitter emitter) throws Exception;
    }
}
