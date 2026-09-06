export type InstallApiError = {
    code?: string;
    message?: string;
};

const parseLegacyErrorCode = (message?: string): string | undefined => {
    if (!message) {
        return undefined;
    }
    return message.match(/\[Error-([A-Z_]+)]/)?.[1];
};

export const resolveInstallDbErrorText = (error: InstallApiError | undefined,
                                          dbErrors: Readonly<Record<string, string>>): string | undefined => {
    const code = error?.code || parseLegacyErrorCode(error?.message);
    return code ? dbErrors[code] : undefined;
};
