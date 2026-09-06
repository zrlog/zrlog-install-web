import {isUpgradeCompletedEvent, isUpgradeEventStream, resolveUpgradeStageKey} from "./upgrade-sse";

describe("UpgradeButton SSE contract", () => {
    test("accepts only event-stream responses", () => {
        expect(isUpgradeEventStream("text/event-stream; charset=utf-8")).toBe(true);
        expect(isUpgradeEventStream("application/json")).toBe(false);
        expect(isUpgradeEventStream("application/text/event-stream")).toBe(false);
        expect(isUpgradeEventStream(null)).toBe(false);
    });

    test("requires an explicit completed terminal event", () => {
        expect(isUpgradeCompletedEvent("upgrade-complete", {finish: true})).toBe(true);
        expect(isUpgradeCompletedEvent("upgrade-progress", {
            stage: "complete",
            status: "complete",
        })).toBe(true);
        expect(isUpgradeCompletedEvent("upgrade-progress", {
            stage: "download",
            status: "complete",
        })).toBe(false);
        expect(isUpgradeCompletedEvent("upgrade-progress", {
            stage: "complete",
            status: "running",
        })).toBe(false);
        expect(isUpgradeCompletedEvent("upgrade-error", {})).toBe(false);
    });

    test("maps only known stage names to user-facing resource keys", () => {
        expect(resolveUpgradeStageKey("download")).toBe("download");
        expect(resolveUpgradeStageKey(" restart ")).toBe("restart");
        expect(resolveUpgradeStageKey("internal-s3-upload-path")).toBe("unknown");
        expect(resolveUpgradeStageKey(undefined)).toBe("unknown");
    });
});
