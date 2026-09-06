import {extractInstallProbeData} from "./install-probe-response";

const validProbe = {
    error: 0,
    data: {
        status: "warning",
        runtimeMode: "zip",
        charset: "UTF-8",
        items: [{code: "runtime.mode", category: "runtime", status: "pass", value: "zip"}],
    },
};

describe("install probe response", () => {
    test("extracts a valid probe response", () => {
        expect(extractInstallProbeData(validProbe)).toEqual(validProbe.data);
    });

    test.each([
        undefined,
        null,
        [],
        {error: 1, data: validProbe.data},
        {error: 0, data: null},
        {error: 0, data: {status: "unknown", items: []}},
        {error: 0, data: {status: "pass", items: {}}},
        {error: 0, data: {status: "pass", items: [{code: "runtime.mode", status: "pass"}]}},
    ])("rejects a malformed probe response: %p", (response) => {
        expect(extractInstallProbeData(response)).toBeUndefined();
    });
});
