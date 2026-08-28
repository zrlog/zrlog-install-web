type UpgradeProgressPayload = {
    stage?: unknown;
    status?: unknown;
};

export type UpgradeStageKey = "download" | "verify" | "upload" | "apply" | "backup" | "restart" |
    "complete" | "unknown";

const UPGRADE_STAGES = new Set<UpgradeStageKey>([
    "download", "verify", "upload", "apply", "backup", "restart", "complete",
]);

export const resolveUpgradeStageKey = (stage: unknown): UpgradeStageKey => {
    if (typeof stage !== "string") {
        return "unknown";
    }
    const normalized = stage.trim().toLowerCase() as UpgradeStageKey;
    return UPGRADE_STAGES.has(normalized) ? normalized : "unknown";
};

export const isUpgradeEventStream = (contentType: string | null): boolean =>
    contentType?.split(";", 1)[0].trim().toLowerCase() === "text/event-stream";

export const isUpgradeCompletedEvent = (eventName: string, data: unknown): boolean => {
    if (eventName === "upgrade-complete") {
        return true;
    }
    if (eventName !== "upgrade-progress" || !data || typeof data !== "object") {
        return false;
    }
    const progress = data as UpgradeProgressPayload;
    return progress.stage === "complete" && progress.status === "complete";
};
