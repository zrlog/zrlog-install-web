const DB_PROPERTIES_HEADING = /^#{1,6}[ \t]+`?DB_PROPERTIES`?[ \t]*\r?$/im;
const PROPERTIES_FENCE = /```(?:properties)?[ \t]*\r?\n([\s\S]*?)\r?\n```/i;

export const extractDbProperties = (content: string): string | undefined => {
    const heading = DB_PROPERTIES_HEADING.exec(content);
    if (!heading) {
        return undefined;
    }
    const afterHeading = content.substring(heading.index + heading[0].length);
    const propertiesFence = PROPERTIES_FENCE.exec(afterHeading);
    if (!propertiesFence) {
        return undefined;
    }
    const nextHeadingIndex = afterHeading.search(/^#{1,6}[ \t]+/m);
    if (nextHeadingIndex >= 0 && nextHeadingIndex < propertiesFence.index) {
        return undefined;
    }
    return propertiesFence[1];
};
