import {
    installApiUrl,
    installMutationHeaders,
    installTokenForRequest,
    installTokenStorageKey,
    isRequiredInstallTokenMissing,
    readInstallResultContent,
    readInstallToken,
    requestInstallCompletion,
    requestResumeInstall,
    saveInstallToken,
} from "./install-api";

describe("install API request contract", () => {
    test("resolves API actions from the application base path", () => {
        expect(installApiUrl("probe", "https://example.com/"))
            .toBe("https://example.com/api/install/probe");
        expect(installApiUrl("/startInstall", "https://example.com/blog/"))
            .toBe("https://example.com/blog/api/install/startInstall");
    });

    test("keeps the trimmed installation token in a request header", () => {
        expect(installMutationHeaders("  install-token  ", "text/event-stream")).toEqual({
            "Content-Type": "application/json",
            Accept: "text/event-stream",
            "X-ZrLog-Install-Token": "install-token",
        });
        expect(installMutationHeaders("   ")).toEqual({"Content-Type": "application/json"});
    });

    test("uses an installation passcode only when deployment explicitly requires it", () => {
        expect(isRequiredInstallTokenMissing(false, "")).toBe(false);
        expect(isRequiredInstallTokenMissing(true, "   ")).toBe(true);
        expect(isRequiredInstallTokenMissing(true, " passcode ")).toBe(false);
        expect(installTokenForRequest(false, "stale-passcode")).toBe("");
        expect(installTokenForRequest(true, "  passcode  ")).toBe("passcode");
        expect(installMutationHeaders(installTokenForRequest(false, "stale-passcode")))
            .toEqual({"Content-Type": "application/json"});
    });

    test("stores the installation token only through the supplied session storage", () => {
        const values = new Map();
        const storage = {
            getItem: jest.fn((key) => values.get(key) || null),
            setItem: jest.fn((key, value) => values.set(key, value)),
            removeItem: jest.fn((key) => values.delete(key)),
        };

        saveInstallToken("  install-token  ", storage);
        expect(storage.setItem).toHaveBeenCalledWith(installTokenStorageKey, "install-token");
        expect(readInstallToken(storage)).toBe("install-token");

        saveInstallToken("  ", storage);
        expect(storage.removeItem).toHaveBeenCalledWith(installTokenStorageKey);
        expect(readInstallToken(storage)).toBe("");
    });

    test("retrieves askConfig completion content with POST JSON and a token header", async () => {
        const fetcher = jest.fn().mockResolvedValue({ok: true});

        await requestInstallCompletion("  install-token  ", fetcher, "https://example.com/blog/");

        expect(fetcher).toHaveBeenCalledWith("https://example.com/blog/api/install/installCompletion", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "X-ZrLog-Install-Token": "install-token",
            },
            body: "{}",
        });
    });

    test("resumes a recoverable installation with POST JSON and no token in the body", async () => {
        const fetcher = jest.fn().mockResolvedValue({ok: true});

        await requestResumeInstall("  install-token  ", fetcher, "https://example.com/blog/");

        expect(fetcher).toHaveBeenCalledWith("https://example.com/blog/api/install/resumeInstall", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "X-ZrLog-Install-Token": "install-token",
            },
            body: "{}",
        });
    });

    test("submits completion and recovery without a passcode when protection is disabled", async () => {
        const fetcher = jest.fn().mockResolvedValue({ok: true});

        await requestInstallCompletion("", fetcher, "https://example.com/blog/");
        await requestResumeInstall("", fetcher, "https://example.com/blog/");

        for (const [url, request] of fetcher.mock.calls) {
            expect(url).toMatch(/\/api\/install\/(installCompletion|resumeInstall)$/);
            expect(request).toEqual({
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: "{}",
            });
        }
    });

    test("accepts only the standard successful install result contract", () => {
        expect(readInstallResultContent({error: 0, data: {content: "configuration"}}))
            .toBe("configuration");
        expect(readInstallResultContent({error: 0, data: {content: ""}})).toBe("");
        expect(readInstallResultContent({error: 1, data: {content: "ignored"}})).toBeUndefined();
        expect(readInstallResultContent({error: 0, data: {}})).toBeUndefined();
    });
});
