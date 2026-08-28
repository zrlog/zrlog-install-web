import {resolveInstallRuntimeView, shouldShowConfigurationHandoff} from "./install-runtime-state";

describe("install runtime state", () => {
    test("prioritizes a completed installation over transient state", () => {
        expect(resolveInstallRuntimeView({
            installed: true,
            installRecoveryAvailable: true,
            installOperationInProgress: true,
        })).toBe("installed");
    });

    test("opens recovery only when the server confirms a recovery point", () => {
        expect(resolveInstallRuntimeView({installRecoveryAvailable: true})).toBe("recoverable");
        expect(resolveInstallRuntimeView({installRecoveryAvailable: false})).toBe("idle");
    });

    test("keeps an active operation distinct from a recoverable failure", () => {
        expect(resolveInstallRuntimeView({installOperationInProgress: true})).toBe("in-progress");
    });

    test("shows configuration handoff only while askConfig configuration is missing", () => {
        expect(shouldShowConfigurationHandoff({askConfig: true, missingConfig: true})).toBe(true);
        expect(shouldShowConfigurationHandoff({askConfig: true, missingConfig: false})).toBe(false);
        expect(shouldShowConfigurationHandoff({askConfig: false, missingConfig: true})).toBe(false);
        expect(shouldShowConfigurationHandoff({askConfig: true})).toBe(false);
    });
});
