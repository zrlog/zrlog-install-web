import DOMPurify from "dompurify";
import {marked} from "marked";

const sanitizeOptions = {
    USE_PROFILES: {html: true},
    FORBID_TAGS: ["style", "form", "input", "button", "textarea", "select", "option", "iframe", "object", "embed"],
    FORBID_ATTR: ["style"],
};

export const sanitizeRichHtml = (html: string): string => DOMPurify.sanitize(html, sanitizeOptions);

export const renderSanitizedMarkdown = (markdown: string): string => {
    const rendered = marked.parse(markdown || "", {async: false});
    return sanitizeRichHtml(typeof rendered === "string" ? rendered : "");
};
