const express = require("express");
const paths = require("react-scripts/config/paths");
const redirectServedPath = require("react-dev-utils/redirectServedPathMiddleware");
const noopServiceWorker = require("react-dev-utils/noopServiceWorkerMiddleware");

// CRA 5 emits webpack-dev-server 4 options. Keep its proxy/source-map hooks
// and service-worker cleanup while using the maintained v5 security fixes.
module.exports = (config) => {
    const { https, onBeforeSetupMiddleware, onAfterSetupMiddleware, ...options } = config;
    options.server = https
        ? { type: "https", options: typeof https === "object" ? https : {} }
        : "http";
    // CRA disables host checks without a proxy and adds wildcard CORS headers.
    // Retain v5's host/origin checks instead of overriding its security defaults.
    options.allowedHosts = "auto";
    delete options.headers;
    options.setupMiddlewares = (middlewares, server) => {
        if (onBeforeSetupMiddleware) {
            // Register CRA hooks inside the middleware chain, after WDS security
            // checks. Calling them with server.app would bypass those checks.
            const app = express.Router();
            const compatServer = Object.create(server);
            compatServer.app = app;
            Object.defineProperty(compatServer, "_stats", {
                get: () => server.stats || server.middleware.context.stats,
            });
            onBeforeSetupMiddleware(compatServer);
            const index = middlewares.findIndex(({ name }) => name === "webpack-dev-middleware");
            if (index < 0) throw new Error("webpack-dev-middleware is missing from the development server");
            middlewares.splice(index, 0, { name: "cra-before", middleware: app });
        }
        return [
            ...middlewares,
            { name: "cra-redirect", middleware: redirectServedPath(paths.publicUrlOrPath) },
            { name: "cra-service-worker", middleware: noopServiceWorker(paths.publicUrlOrPath) },
        ];
    };
    return options;
};
