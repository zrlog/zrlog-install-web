import zh_CN from "antd/es/locale/zh_CN";
import en_US from "antd/es/locale/en_US";
import {App, ConfigProvider} from "antd";
import AppBase from "./AppBase";
import {useEffect, useLayoutEffect, useState} from "react";
import EnvUtils from "./utils/env-utils";
import {BrowserRouter} from "react-router-dom";
import {legacyLogicalPropertiesTransformer, StyleProvider} from "@ant-design/cssinjs";
import {createRoot} from "react-dom/client";
import {getRes, resourceKey} from "./utils/constants";
import {isInstallRuntimeResource, normalizeInstallRuntimeResource} from "./utils/install-resource-response";
import useAdminDefaultTheme from "./theme/admin-default-theme";

const jsonStr = document.getElementById("resourceInfo")?.textContent;
export let resLoadedBySsr = false;
const resourceChangeEvent = "zrlog-install-resource-change";

let lang = "zh_CN";

export const setRes = (data: Record<string, unknown>) => {
    //@ts-ignore
    window[resourceKey] = JSON.stringify(data);
    lang = getRes().lang || "zh_CN";
    document.documentElement.lang = lang.startsWith("zh") ? "zh-CN" : "en";
    document.title = getRes().wizard.title;
    window.dispatchEvent(new CustomEvent(resourceChangeEvent));
}

if (jsonStr && jsonStr !== "") {
    try {
        const resource = JSON.parse(jsonStr);
        const runtimeResource = normalizeInstallRuntimeResource(resource);
        if (runtimeResource && isInstallRuntimeResource(resource) && Object.keys(resource).length > 0) {
            setRes(runtimeResource);
            resLoadedBySsr = true;
        }
    } catch {
        // AppBase will request a fresh resource and expose its retry state if that fails.
    }
}

const Index = () => {
    const [themeMode, setThemeMode] = useState(EnvUtils.getThemeMode);
    const [currentLang, setCurrentLang] = useState(lang);
    const [systemDark, setSystemDark] = useState(() => EnvUtils.getPreferredColorScheme() === "dark");
    const dark = themeMode === "system" ? systemDark : themeMode === "dark";
    const themeConfig = useAdminDefaultTheme(dark);

    useLayoutEffect(() => {
        document.documentElement.dataset.installTheme = dark ? "dark" : "light";
        document.querySelector<HTMLMetaElement>('meta[name="theme-color"]')
            ?.setAttribute("content", dark ? "#141414" : "#f5f9ff");
    }, [dark]);

    useEffect(() => {
        const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
        const systemThemeChangeHandler = (event: MediaQueryListEvent) => setSystemDark(event.matches);
        const themeModeChangeHandler = () => setThemeMode(EnvUtils.getThemeMode());
        const resourceChangeHandler = () => setCurrentLang(lang);

        mediaQuery.addEventListener('change', systemThemeChangeHandler);
        window.addEventListener(EnvUtils.themeModeChangeEvent, themeModeChangeHandler);
        window.addEventListener('storage', themeModeChangeHandler);
        window.addEventListener(resourceChangeEvent, resourceChangeHandler);

        return () => {
            mediaQuery.removeEventListener('change', systemThemeChangeHandler);
            window.removeEventListener(EnvUtils.themeModeChangeEvent, themeModeChangeHandler);
            window.removeEventListener('storage', themeModeChangeHandler);
            window.removeEventListener(resourceChangeEvent, resourceChangeHandler);
        };
    }, []);

    return (
        <ConfigProvider
            locale={currentLang.startsWith("zh") ? zh_CN : en_US}
            {...themeConfig}
        >
            <App>
                <BrowserRouter>
                    <StyleProvider transformers={[legacyLogicalPropertiesTransformer]}>
                        <AppBase/>
                    </StyleProvider>
                </BrowserRouter>
            </App>
        </ConfigProvider>
    );
};

const container = document.getElementById("app");
const root = createRoot(container!); // createRoot(container!) if you use TypeScript
root.render(<Index/>);
