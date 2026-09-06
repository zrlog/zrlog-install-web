import {
    extractInstallRuntimeResource,
    isInstallRuntimeResource,
    normalizeInstallRuntimeResource,
} from "./install-resource-response";

describe("install resource response", () => {
    test("returns a successful resource payload", () => {
        const resource = {lang: "zh_CN", installed: false};
        expect(extractInstallRuntimeResource({error: 0, data: resource})).toEqual({
            ...resource,
            installTokenRequired: false,
        });
    });

    test("requires the installation passcode only for an explicit boolean true", () => {
        expect(normalizeInstallRuntimeResource({installTokenRequired: true})?.installTokenRequired).toBe(true);
        expect(normalizeInstallRuntimeResource({})?.installTokenRequired).toBe(false);
        expect(normalizeInstallRuntimeResource({installTokenRequired: "true"})?.installTokenRequired).toBe(false);
    });

    test.each([
        undefined,
        null,
        [],
        {error: 1, data: {}},
        {error: 0},
        {error: 0, data: null},
        {error: 0, data: []},
        {error: "0", data: {}},
    ])("rejects a malformed response: %p", (response) => {
        expect(extractInstallRuntimeResource(response)).toBeUndefined();
    });

    test.each([{}, {installed: false}])("accepts an object as an SSR runtime resource: %p", (resource) => {
        expect(isInstallRuntimeResource(resource)).toBe(true);
    });

    test.each([undefined, null, [], "{}", 0])("rejects an invalid SSR runtime resource: %p", (resource) => {
        expect(isInstallRuntimeResource(resource)).toBe(false);
    });
});
