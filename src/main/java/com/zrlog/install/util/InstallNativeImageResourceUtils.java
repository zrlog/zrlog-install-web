package com.zrlog.install.util;

import com.google.gson.Gson;
import com.hibegin.http.server.util.NativeImageUtils;
import com.zrlog.install.business.response.*;
import com.zrlog.install.business.vo.InstallDatabaseConfig;
import com.zrlog.install.business.vo.InstallConfigVO;
import com.zrlog.install.business.vo.InstallSiteConfig;
import com.zrlog.install.business.vo.InstallSuccessData;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class InstallNativeImageResourceUtils {

    public static void reg() {
        List<String> resourceNameList = new ArrayList<>();
        resourceNameList.add("/i18n/init-blog/en_US.md");
        resourceNameList.add("/i18n/init-blog/en_US.html");
        resourceNameList.add("/i18n/init-blog/zh_CN.md");
        resourceNameList.add("/i18n/init-blog/zh_CN.html");
        resourceNameList.add("/i18n/installed-faas/zh_CN.md");
        resourceNameList.add("/i18n/installed-docker/zh_CN.md");
        resourceNameList.add("/i18n/install_en_US.properties");
        resourceNameList.add("/i18n/install_zh_CN.properties");
        try (InputStream assetJson = InstallNativeImageResourceUtils.class.getResourceAsStream("/install/asset-manifest.json")) {
            InstallAssetManifest assetManifest = new Gson().fromJson(new String(assetJson.readAllBytes()), InstallAssetManifest.class);
            if (assetManifest != null && Objects.nonNull(assetManifest.getFiles())) {
                resourceNameList.addAll(assetManifest.getFiles().values().stream().map(e -> {
                    return e.replaceFirst("\\./", "/");
                }).collect(Collectors.toList()));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        resourceNameList.add("/init-table-structure.sql");
        NativeImageUtils.doResourceLoadByResourceNames(resourceNameList);
        NativeImageUtils.gsonNativeAgentByClazz(Arrays.asList(InstalledResResponse.class,
                LastVersionInfo.class, InstallResultResponse.class, InstallUpgradeResult.class,
                TestConnectResponse.class, InstallResourceResponse.class,
                InstallRuntimeResourceResponse.class, InstallProbeResponse.class,
                InstallAssetManifest.class,
                InstallApiResponses.Empty.class, InstallApiResponses.Message.class, InstallApiResponses.Error.class,
                InstallProbeData.class, InstallProbeItem.class, InstallProgressEvent.class,
                //vo
                InstallConfigVO.class, InstallDatabaseConfig.class, InstallSiteConfig.class,
                InstallSuccessData.class));
    }

    public static void main(String[] args) {
        reg();
    }
}
