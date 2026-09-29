import type { ColorMode as ThemeMode } from "@zrlog/ui";
export type { ColorMode as ThemeMode } from "@zrlog/ui";

class EnvUtils {

    static themeModeStorageKey = "zrlog-install-theme-mode";
    static themeModeChangeEvent = "zrlog-install-theme-mode-change";

    static getPreferredColorScheme() {
        if (window.matchMedia) {
            if (window.matchMedia('(prefers-color-scheme: dark)').matches) {
                return 'dark';
            }
        }
        return 'light';
    }

    static getThemeMode(): ThemeMode {
        try {
            const themeMode = window.localStorage.getItem(EnvUtils.themeModeStorageKey);
            if (themeMode === "light" || themeMode === "dark" || themeMode === "system") {
                return themeMode;
            }
        } catch (error) {
            return "light";
        }
        return "light";
    }

    static setThemeMode(themeMode: ThemeMode) {
        try {
            window.localStorage.setItem(EnvUtils.themeModeStorageKey, themeMode);
        } catch (error) {
            return;
        }
        window.dispatchEvent(new CustomEvent(EnvUtils.themeModeChangeEvent, {detail: themeMode}));
    }

    static isDarkMode(themeMode: ThemeMode = EnvUtils.getThemeMode()) {
        if (themeMode === "dark") {
            return true;
        }
        if (themeMode === "system") {
            return EnvUtils.getPreferredColorScheme() === "dark";
        }
        return false;
    }
}

export default EnvUtils;
