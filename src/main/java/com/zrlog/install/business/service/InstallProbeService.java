package com.zrlog.install.business.service;

import com.zrlog.install.business.response.InstallProbeData;
import com.zrlog.install.business.response.InstallProbeItem;
import com.zrlog.install.web.config.InstallConfig;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class InstallProbeService {

    public InstallProbeData probe(InstallConfig installConfig) {
        InstallProbeData data = new InstallProbeData();
        data.setRuntimeMode(getRuntimeMode(installConfig));
        data.setCharset(Charset.defaultCharset().displayName());

        List<InstallProbeItem> items = new ArrayList<>();
        items.add(new InstallProbeItem("runtime.mode", "runtime", "pass", data.getRuntimeMode()));
        items.add(new InstallProbeItem("runtime.charset", "runtime", isUtfCharset() ? "pass" : "warning", data.getCharset()));
        items.add(fileProbe("file.dbProperties", installConfig.getDbPropertiesFile()));
        items.add(fileProbe("file.installLock", installConfig.getAction().getLockFile()));
        if (isDockerMode()) {
            items.add(new InstallProbeItem("runtime.dockerMount", "runtime", "warning", "docker"));
        }
        if (Objects.equals(data.getRuntimeMode(), "faas")) {
            items.add(new InstallProbeItem("runtime.faasAskConfig", "runtime", "warning", "askConfig"));
        }
        data.setItems(items);
        data.setStatus(summaryStatus(items));
        return data;
    }

    public static String getRuntimeMode(InstallConfig installConfig) {
        if (isFaasMode()) {
            return "faas";
        }
        if (isDockerMode()) {
            return "docker";
        }
        if (installConfig.isWarMode()) {
            return "war";
        }
        if (System.getProperty("org.graalvm.nativeimage.imagecode") != null) {
            return "native";
        }
        return "zip";
    }

    private InstallProbeItem fileProbe(String code, File targetFile) {
        File parent = targetFile.getParentFile();
        if (targetFile.exists() && !targetFile.canWrite()) {
            return new InstallProbeItem(code, "file", "block", "file-not-writable");
        }
        if (!canCreateIn(parent)) {
            return new InstallProbeItem(code, "file", "block", "parent-not-writable");
        }
        String value = targetFile.exists() ? "file-writable" : "file-creatable";
        return new InstallProbeItem(code, "file", "pass", value);
    }

    private boolean canCreateIn(File directory) {
        if (directory == null) {
            return false;
        }
        File cursor = directory;
        while (cursor != null && !cursor.exists()) {
            cursor = cursor.getParentFile();
        }
        return cursor != null && cursor.isDirectory() && cursor.canWrite();
    }

    private String summaryStatus(List<InstallProbeItem> items) {
        if (items.stream().anyMatch(item -> Objects.equals(item.getStatus(), "block"))) {
            return "block";
        }
        if (items.stream().anyMatch(item -> Objects.equals(item.getStatus(), "warning"))) {
            return "warning";
        }
        return "pass";
    }

    private boolean isUtfCharset() {
        return Charset.defaultCharset().displayName().toLowerCase(Locale.ROOT).contains("utf");
    }

    private static boolean isDockerMode() {
        return "true".equalsIgnoreCase(System.getenv("DOCKER_MODE"));
    }

    private static boolean isFaasMode() {
        return System.getenv("AWS_LAMBDA_FUNCTION_NAME") != null
                || System.getenv("LAMBDA_TASK_ROOT") != null
                || System.getenv("FC_FUNC_CODE_PATH") != null;
    }
}
