import type {InstallRuntimeResourceInfo} from "./constants";

export type InstallRuntimeView = "installed" | "recoverable" | "in-progress" | "idle";

export const shouldShowConfigurationHandoff = (resource: InstallRuntimeResourceInfo): boolean =>
    resource.askConfig === true && resource.missingConfig === true;

export const resolveInstallRuntimeView = (resource: InstallRuntimeResourceInfo): InstallRuntimeView => {
    if (resource.installed === true) {
        return "installed";
    }
    if (resource.installRecoveryAvailable === true) {
        return "recoverable";
    }
    if (resource.installOperationInProgress === true) {
        return "in-progress";
    }
    return "idle";
};
