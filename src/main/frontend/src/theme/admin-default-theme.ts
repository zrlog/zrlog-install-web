import type {ConfigProviderProps} from "antd";
import {theme} from "antd";
import {useMemo} from "react";

const {darkAlgorithm, defaultAlgorithm} = theme;

export const adminDefaultPrimaryColor = "#1677ff";

const useAdminDefaultTheme = (dark: boolean): ConfigProviderProps => useMemo(() => ({
    theme: {
        algorithm: [dark ? darkAlgorithm : defaultAlgorithm],
        cssVar: {prefix: "ant"},
        token: {
            colorPrimary: adminDefaultPrimaryColor,
            wireframe: false,
            lineHeight: 1.5,
            borderRadius: 12,
            borderRadiusSM: 8,
            borderRadiusLG: 16,
            borderRadiusXS: 8,
            controlHeight: 40,
            boxShadow: dark
                ? "0 1px 2px rgba(0,0,0,0.45), 0 1px 3px 1px rgba(0,0,0,0.35)"
                : "0 1px 2px rgba(0,0,0,0.10), 0 1px 3px 1px rgba(0,0,0,0.07)",
            boxShadowSecondary: dark
                ? "0 1px 2px rgba(0,0,0,0.45), 0 2px 6px 2px rgba(0,0,0,0.35)"
                : "0 1px 2px rgba(0,0,0,0.10), 0 2px 6px 2px rgba(0,0,0,0.07)",
            colorBgLayout: dark ? "rgba(26, 26, 26, 0.85)" : "rgba(22, 119, 255, 0.04)",
        },
        components: {
            Button: {
                borderRadius: 20,
                controlHeight: 40,
                fontWeight: 500,
                primaryShadow: dark
                    ? "0 1px 2px rgba(0,0,0,0.45)"
                    : `0 1px 2px ${adminDefaultPrimaryColor}40`,
                defaultShadow:
                    "0px 3px 1px -2px rgba(0,0,0,0.2), 0px 2px 2px 0px rgba(0,0,0,0.14), 0px 1px 5px 0px rgba(0,0,0,0.12)",
                dangerShadow:
                    "0px 3px 1px -2px rgba(0,0,0,0.2), 0px 2px 2px 0px rgba(0,0,0,0.14), 0px 1px 5px 0px rgba(0,0,0,0.12)",
                paddingInline: 16,
            },
            Input: {
                borderRadius: 12,
                controlHeight: 40,
                activeShadow: `0 0 0 2px ${adminDefaultPrimaryColor}30`,
            },
            InputNumber: {
                borderRadius: 12,
                controlHeight: 40,
                activeShadow: `0 0 0 2px ${adminDefaultPrimaryColor}30`,
            },
            Card: {
                bodyPadding: 12,
                borderRadiusLG: 16,
                paddingLG: 20,
                boxShadow: dark
                    ? "0 1px 2px rgba(0,0,0,0.45), 0 1px 3px 1px rgba(0,0,0,0.35)"
                    : "0 1px 2px rgba(0,0,0,0.10), 0 1px 3px 1px rgba(0,0,0,0.07)",
                boxShadowTertiary: dark
                    ? "0 1px 2px rgba(0,0,0,0.45), 0 2px 6px 2px rgba(0,0,0,0.35)"
                    : "0 1px 2px rgba(0,0,0,0.10), 0 2px 6px 2px rgba(0,0,0,0.07)",
            },
            Form: {
                itemMarginBottom: 20,
                verticalLabelPadding: "0 0 10px",
            },
            Modal: {
                borderRadiusLG: 28,
            },
            Alert: {
                borderRadius: 8,
            },
            Select: {
                borderRadius: 12,
                controlHeight: 40,
            },
        },
    },
}), [dark]);

export default useAdminDefaultTheme;
