const normalizeAction = (action: string): string => action.replace(/^\/+/, "");

export const installTokenStorageKey = "zrlog-install-token";

type InstallTokenStorage = Pick<Storage, "getItem" | "setItem" | "removeItem">;

const getSessionStorage = (): InstallTokenStorage | undefined => {
    if (typeof window === "undefined") {
        return undefined;
    }
    try {
        return window.sessionStorage;
    } catch (error) {
        return undefined;
    }
};

export const readInstallToken = (storage: InstallTokenStorage | undefined = getSessionStorage()): string => {
    if (!storage) {
        return "";
    }
    try {
        return storage.getItem(installTokenStorageKey) || "";
    } catch (error) {
        return "";
    }
};

export const saveInstallToken = (installToken: string,
                                 storage: InstallTokenStorage | undefined = getSessionStorage()): void => {
    if (!storage) {
        return;
    }
    const normalizedToken = installToken.trim();
    try {
        if (normalizedToken) {
            storage.setItem(installTokenStorageKey, normalizedToken);
        } else {
            storage.removeItem(installTokenStorageKey);
        }
    } catch (error) {
        // Storage can be unavailable in privacy-restricted browser contexts.
    }
};

export const installApiUrl = (action: string, baseUrl: string = document.baseURI): string =>
    new URL(`api/install/${normalizeAction(action)}`, baseUrl).toString();

export const installMutationHeaders = (installToken?: string, accept?: string): Record<string, string> => ({
    "Content-Type": "application/json",
    ...(accept ? {Accept: accept} : {}),
    ...(installToken?.trim() ? {"X-ZrLog-Install-Token": installToken.trim()} : {}),
});

export const isRequiredInstallTokenMissing = (installTokenRequired: boolean, installToken: string): boolean =>
    installTokenRequired && !installToken.trim();

export const installTokenForRequest = (installTokenRequired: boolean, installToken: string): string =>
    installTokenRequired ? installToken.trim() : "";

const requestEmptyJsonMutation = (action: string,
                                  installToken: string,
                                  fetcher: typeof fetch,
                                  baseUrl: string): Promise<Response> =>
    fetcher(installApiUrl(action, baseUrl), {
        method: "POST",
        headers: installMutationHeaders(installToken),
        body: "{}",
    });

export const requestInstallCompletion = (installToken: string,
                                         fetcher: typeof fetch = fetch,
                                         baseUrl: string = document.baseURI): Promise<Response> =>
    requestEmptyJsonMutation("installCompletion", installToken, fetcher, baseUrl);

export const requestResumeInstall = (installToken: string,
                                     fetcher: typeof fetch = fetch,
                                     baseUrl: string = document.baseURI): Promise<Response> =>
    requestEmptyJsonMutation("resumeInstall", installToken, fetcher, baseUrl);

export const readInstallResultContent = (responseBody: unknown): string | undefined => {
    if (!responseBody || typeof responseBody !== "object") {
        return undefined;
    }
    const result = responseBody as {error?: unknown, data?: {content?: unknown}};
    return result.error === 0 && typeof result.data?.content === "string" ? result.data.content : undefined;
};
