import {buildInstallHandoffUrls, extractDbProperties} from "./install-completion";

describe("install completion content", () => {
    test("extracts the complete multiline DB_PROPERTIES value", () => {
        const markdown = [
            "#### One more step",
            "",
            "##### `DB_PROPERTIES`",
            "",
            "Set the complete value below:",
            "",
            "```properties",
            "# generated config",
            "jdbcUrl=https\\://db.example.test/zrlog",
            "password=a=b=c",
            "",
            "```",
            "",
            "##### `TZ`",
            "",
            "```text",
            "Asia/Shanghai",
            "```",
        ].join("\n");

        expect(extractDbProperties(markdown)).toBe([
            "# generated config",
            "jdbcUrl=https\\://db.example.test/zrlog",
            "password=a=b=c",
            "",
        ].join("\n"));
    });

    test("does not mistake another code block for DB_PROPERTIES", () => {
        expect(extractDbProperties("```text\nAsia/Shanghai\n```")).toBeUndefined();
    });

    test("does not copy a code block from a later configuration section", () => {
        const markdown = [
            "##### `DB_PROPERTIES`",
            "",
            "The value is temporarily unavailable.",
            "",
            "##### `TZ`",
            "",
            "```text",
            "Asia/Shanghai",
            "```",
        ].join("\n");

        expect(extractDbProperties(markdown)).toBeUndefined();
    });

    test("builds context-aware admin, create, and Markdown import handoff URLs", () => {
        expect(buildInstallHandoffUrls("https://example.test/blog/install")).toEqual({
            admin: "https://example.test/blog/admin/",
            createArticle: "https://example.test/blog/admin/article-edit",
            importMarkdown: "https://example.test/blog/admin/article-edit?intent=import-markdown",
        });
        expect(buildInstallHandoffUrls("https://example.test/install").importMarkdown).toBe(
            "https://example.test/admin/article-edit?intent=import-markdown",
        );
    });
});
