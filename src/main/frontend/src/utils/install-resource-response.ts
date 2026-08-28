import type {InstallRuntimeResourceInfo} from "./constants";

export const isInstallRuntimeResource = (value: unknown): value is Record<string, unknown> =>
    typeof value === "object" && value !== null && !Array.isArray(value);

export type NormalizedInstallRuntimeResource = InstallRuntimeResourceInfo & Record<string, unknown>;

export const normalizeInstallRuntimeResource = (value: unknown): NormalizedInstallRuntimeResource | undefined => {
    if (!isInstallRuntimeResource(value)) {
        return undefined;
    }
    return {
        ...value,
        installTokenRequired: value.installTokenRequired === true,
    } as NormalizedInstallRuntimeResource;
};

export const extractInstallRuntimeResource = (payload: unknown): NormalizedInstallRuntimeResource | undefined => {
    if (!isInstallRuntimeResource(payload) || payload.error !== 0 ||
        !isInstallRuntimeResource(payload.data)) {
        return undefined;
    }
    return normalizeInstallRuntimeResource(payload.data);
};
