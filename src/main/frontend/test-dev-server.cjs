const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const http = require("node:http");
const https = require("node:https");
const { test } = require("node:test");

process.env.NODE_ENV = "development";
const webpack = require("webpack");
const DevServer = require("webpack-dev-server");
const createCraConfig = require("react-scripts/config/webpackDevServer.config");
const paths = require("react-scripts/config/paths");
const adaptConfig = require("./dev-server-config.cjs");

function request(protocol, port, pathname, headers = {}) {
    return new Promise((resolve, reject) => {
        const client = protocol === "https" ? https : http;
        const req = client.get({ hostname: "127.0.0.1", port, path: pathname, headers,
            rejectUnauthorized: false, timeout: 5000 }, (res) => {
            let body = "";
            res.setEncoding("utf8");
            res.on("data", (chunk) => { body += chunk; });
            res.on("end", () => resolve({ status: res.statusCode, body, headers: res.headers }));
        });
        req.on("error", reject);
        req.on("timeout", () => req.destroy(new Error("development server request timed out")));
    });
}

for (const protocol of ["http", "https"]) {
    test(`CRA development middleware works over ${protocol} and rejects hostile hosts`, async (t) => {
        const directory = fs.mkdtempSync(path.join(os.tmpdir(), "zrlog-dev-server-"));
        fs.writeFileSync(path.join(directory, "entry.js"), 'console.log("development fixture");');
        const compiler = webpack({
            mode: "development", entry: path.join(directory, "entry.js"),
            output: { path: path.join(directory, "dist"), filename: "bundle.js", publicPath: paths.publicUrlOrPath },
            infrastructureLogging: { level: "error" }, stats: "errors-only",
        });
        const cra = createCraConfig(undefined, "localhost");
        cra.https = protocol === "https";
        const before = cra.onBeforeSetupMiddleware;
        cra.onBeforeSetupMiddleware = (server) => {
            before(server);
            server.app.get("/__compat-before", (_req, res) => res.send("hook preserved"));
        };
        const server = new DevServer({ ...adaptConfig(cra), host: "127.0.0.1", port: 0,
            open: false, hot: false, client: false, webSocketServer: false,
        }, compiler);
        t.after(async () => {
            await server.stop();
            fs.rmSync(directory, { recursive: true, force: true });
        });
        await server.start();
        const port = server.server.address().port;
        const hook = await request(protocol, port, "/__compat-before");
        assert.equal(hook.status, 200);
        assert.equal(hook.body, "hook preserved");
        const bundle = await request(protocol, port, paths.publicUrlOrPath + "bundle.js");
        assert.equal(bundle.status, 200);
        assert.match(bundle.body, /development fixture/);
        assert.notEqual(bundle.headers["access-control-allow-origin"], "*");
        const worker = await request(protocol, port, paths.publicUrlOrPath + "service-worker.js");
        assert.equal(worker.status, 200);
        assert.match(worker.body, /skipWaiting/);
        for (const pathname of [paths.publicUrlOrPath + "bundle.js", "/__compat-before", "/__get-internal-source"]) {
            const hostile = await request(protocol, port, pathname, { Host: "attacker.invalid" });
            assert.equal(hostile.status, 403, pathname);
            const malformed = await request(protocol, port, pathname, { Host: "[malformed" });
            assert.equal(malformed.status, 403, pathname);
            const crossSite = await request(protocol, port, pathname, {
                Origin: "https://attacker.invalid", "Sec-Fetch-Site": "cross-site", "Sec-Fetch-Mode": "no-cors",
            });
            assert.equal(crossSite.status, 403, pathname);
        }
        const compilation = server.middleware.context.stats.compilation;
        const entry = Array.from(compilation.modules).find((module) => module.resource === path.join(directory, "entry.js"));
        const source = await request(protocol, port, "/__get-internal-source?fileName=" +
            encodeURIComponent("webpack-internal:///" + compilation.chunkGraph.getModuleId(entry)));
        assert.equal(source.status, 200);
        assert.match(source.body, /development fixture/);
    });
}
