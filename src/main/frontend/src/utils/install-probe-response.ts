export type ProbeStatus = "pass" | "warning" | "block";

export type InstallProbeItem = {
    code: string;
    category: string;
    status: ProbeStatus;
    value?: string;
};

export type InstallProbeData = {
    status: ProbeStatus;
    runtimeMode?: string;
    charset?: string;
    items: InstallProbeItem[];
};

const isRecord = (value: unknown): value is Record<string, unknown> =>
    typeof value === "object" && value !== null && !Array.isArray(value);

const isProbeStatus = (value: unknown): value is ProbeStatus =>
    value === "pass" || value === "warning" || value === "block";

const extractProbeItem = (value: unknown): InstallProbeItem | undefined => {
    if (!isRecord(value) || typeof value.code !== "string" || typeof value.category !== "string" ||
        !isProbeStatus(value.status) ||
        (value.value !== undefined && value.value !== null && typeof value.value !== "string")) {
        return undefined;
    }
    return {
        code: value.code,
        category: value.category,
        status: value.status,
        value: typeof value.value === "string" ? value.value : undefined,
    };
};

export const extractInstallProbeData = (payload: unknown): InstallProbeData | undefined => {
    if (!isRecord(payload) || payload.error !== 0 || !isRecord(payload.data) ||
        !isProbeStatus(payload.data.status) || !Array.isArray(payload.data.items)) {
        return undefined;
    }
    const items = payload.data.items.map(extractProbeItem);
    if (items.some((item) => item === undefined)) {
        return undefined;
    }
    return {
        status: payload.data.status,
        runtimeMode: typeof payload.data.runtimeMode === "string" ? payload.data.runtimeMode : undefined,
        charset: typeof payload.data.charset === "string" ? payload.data.charset : undefined,
        items: items as InstallProbeItem[],
    };
};
