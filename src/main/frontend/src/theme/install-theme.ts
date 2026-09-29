import { DEFAULT_PRIMARY_COLOR } from "@zrlog/ui/themes";
import { createMaterialTheme } from "@zrlog/ui/material";
import { useMemo } from "react";

const useInstallTheme = (dark: boolean) => useMemo(() => {
    const config = createMaterialTheme({ colorPrimary: DEFAULT_PRIMARY_COLOR, dark });
    return { ...config, theme: { ...config.theme, cssVar: { prefix: "ant" } } };
}, [dark]);
export default useInstallTheme;
