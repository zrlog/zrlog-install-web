package com.zrlog.install.util;

import com.hibegin.common.util.LoggerUtil;
import com.zrlog.install.web.InstallConstants;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 多语言的工具类
 */
public class InstallI18nUtil {

    private static final Logger LOGGER = LoggerUtil.getLogger(InstallI18nUtil.class);
    private static final String I18N_INSTALL_KEY = "install";
    private static final Map<String, Map<String, Object>> resMap = new ConcurrentHashMap<>();

    static {
        reloadSystemI18N();
    }

    private static void reloadSystemI18N() {
        loadI18N(InstallI18nUtil.class.getResourceAsStream("/i18n/install_en_US.properties"), "install_en_US.properties");
        loadI18N(InstallI18nUtil.class.getResourceAsStream("/i18n/install_zh_CN.properties"), "install_zh_CN.properties");
    }

    private static void loadI18N(InputStream inputStream, String name) {
        if (Objects.isNull(inputStream)) {
            return;
        }
        if (!name.endsWith(".properties")) {
            return;
        }
        try {
            String key = name.replace(".properties", "").replace("i18n_", "").replace(InstallI18nUtil.I18N_INSTALL_KEY + "_", "");
            Map<String, Object> map = resMap.computeIfAbsent(key, k -> new HashMap<>());
            Properties properties = new Properties();

            properties.load(inputStream);
            for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                map.put(entry.getKey().toString(), entry.getValue());
            }
        } catch (Exception e) {
            InstallLogUtil.logFailure(LOGGER, Level.SEVERE,
                    InstallLogUtil.FailurePhase.I18N_RESOURCE_LOAD, e);
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                InstallLogUtil.logFailure(LOGGER, Level.SEVERE,
                        InstallLogUtil.FailurePhase.I18N_RESOURCE_CLOSE, e);
            }
        }
    }

    public static Map<String, Object> getInstallMap() {
        Map<String, Object> stringObjectMap = resMap.get(InstallConstants.installConfig.getAcceptLanguage());
        if (stringObjectMap == null) {
            return new HashMap<>();
        }
        return new TreeMap<>(stringObjectMap);
    }

    public static String getInstallStringFromRes(String key) {
        Map<String, Object> i18nVO = getInstallMap();
        Object obj = i18nVO.get(key);
        if (obj != null) {
            return obj.toString();
        }
        return "";
    }
}
