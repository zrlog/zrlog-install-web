import {getRes, resourceKey} from "./constants";

describe("install footer resource", () => {
    afterEach(() => {
        delete window[resourceKey];
    });

    test.each(["zh_CN", "en_US"])("keeps the English legal notice and tracked homepage URL for %s", (lang) => {
        window[resourceKey] = JSON.stringify({lang});

        const resource = getRes();
        expect(resource.copyrightTips).toBe(
            "Copyright © 2013–2026 <a target=\"_blank\" href=\"https://www.zrlog.com/?utm_source=zrlog&amp;utm_medium=referral&amp;utm_content=install-footer\">ZrLog</a>",
        );
        expect(resource.copyrightSuffix).toBe(". All rights reserved.");

        const footer = document.createElement("footer");
        footer.innerHTML = `${resource.copyrightTips}${resource.copyrightSuffix}`;
        expect(footer.textContent).toBe("Copyright © 2013–2026 ZrLog. All rights reserved.");
        expect(footer.querySelector("a")?.getAttribute("href")).toBe(
            "https://www.zrlog.com/?utm_source=zrlog&utm_medium=referral&utm_content=install-footer",
        );
    });
});
