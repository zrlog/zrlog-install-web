import {resolveInstallDbErrorText} from "./install-errors";

describe("install database error localization", () => {
    const dbErrors = {
        DATABASE_NOT_EMPTY: "The database already contains a ZrLog table.",
    };

    test("maps DATABASE_NOT_EMPTY returned by testDbConn", () => {
        expect(resolveInstallDbErrorText({code: "DATABASE_NOT_EMPTY"}, dbErrors))
            .toBe(dbErrors.DATABASE_NOT_EMPTY);
    });

    test("maps DATABASE_NOT_EMPTY from install errors and legacy messages", () => {
        expect(resolveInstallDbErrorText({
            code: "DATABASE_NOT_EMPTY",
            message: "internal detail",
        }, dbErrors)).toBe(dbErrors.DATABASE_NOT_EMPTY);
        expect(resolveInstallDbErrorText({
            message: "Install stopped [Error-DATABASE_NOT_EMPTY]",
        }, dbErrors)).toBe(dbErrors.DATABASE_NOT_EMPTY);
    });

    test("does not invent copy for unknown error codes", () => {
        expect(resolveInstallDbErrorText({code: "UNKNOWN_CODE"}, dbErrors)).toBeUndefined();
    });
});
