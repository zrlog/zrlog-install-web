import axios from "axios";
import {ReloadOutlined} from "@ant-design/icons";
import {Button, Layout, Result, Spin, Typography} from "antd";
import {useEffect, useRef, useState} from "react";
import {getRes, hasRuntimeResource} from "./utils/constants";
import IndexLayout from "./components";
import {resLoadedBySsr, setRes} from "./index";
import {installApiUrl} from "./utils/install-api";
import InstallBrand from "./components/InstallBrand";
import {sanitizeRichHtml} from "./utils/sanitize-html";
import {extractInstallRuntimeResource} from "./utils/install-resource-response";

axios.defaults.baseURL = document.baseURI;

type AppState = {
    status: "loading" | "error" | "ready";
};

const AppBase = () => {
    const resourceRequestRef = useRef<AbortController | null>(null);
    const [appState, setAppState] = useState<AppState>({
        status: resLoadedBySsr ? "ready" : "loading",
    });

    const loadResourceFromServer = () => {
        resourceRequestRef.current?.abort();
        const controller = new AbortController();
        resourceRequestRef.current = controller;
        setAppState({status: "loading"});
        const resourceApi = installApiUrl("installResource");
        axios
            .get(resourceApi, {signal: controller.signal, timeout: 10000})
            .then(({data}: { data: unknown }) => {
                if (resourceRequestRef.current !== controller) {
                    return;
                }
                const resource = extractInstallRuntimeResource(data);
                if (resource === undefined) {
                    setAppState({status: "error"});
                    return;
                }
                setRes(resource);
                setAppState({status: "ready"});
            })
            .catch((error) => {
                if (resourceRequestRef.current !== controller || axios.isCancel(error)) {
                    return;
                }
                setAppState({status: "error"});
            })
            .finally(() => {
                if (resourceRequestRef.current === controller) {
                    resourceRequestRef.current = null;
                }
            });
    };

    const initRes = () => {
        if (hasRuntimeResource()) {
            return;
        }
        loadResourceFromServer();
    };


    useEffect(() => {
        initRes();
        return () => {
            resourceRequestRef.current?.abort();
            resourceRequestRef.current = null;
        };
    }, []);

    if (appState.status !== "ready") {
        const res = getRes();
        const failed = appState.status === "error";
        return <Layout className="install-shell">
            <div className="install-page install-bootstrap-page">
                <header className="install-header" role="banner">
                    <InstallBrand/>
                </header>
                <main className="install-bootstrap-main">
                    <div role={failed ? "alert" : "status"} aria-live={failed ? "assertive" : "polite"}>
                        <Result
                            className="install-bootstrap-result"
                            status={failed ? "error" : undefined}
                            icon={failed ? undefined : <Spin size="large"/>}
                            title={<Typography.Title className="install-result-title"
                                                     id="install-bootstrap-title" level={1}>
                                {failed ? res.startup.errorTitle : res.startup.loadingTitle}
                            </Typography.Title>}
                            subTitle={<Typography.Paragraph className="install-result-description">
                                {failed ? res.startup.errorDescription : res.startup.loadingDescription}
                            </Typography.Paragraph>}
                            extra={failed ? <Button className="install-result-action" type="primary" size="large"
                                                    icon={<ReloadOutlined aria-hidden="true"/>}
                                                    onClick={loadResourceFromServer}>
                                {res.common.retry}
                            </Button> : undefined}
                        />
                    </div>
                </main>
            </div>
            <footer className="install-footer">
                <span dangerouslySetInnerHTML={{__html: sanitizeRichHtml(res.copyrightTips)}}/>
                {res.copyrightSuffix}
            </footer>
        </Layout>;
    }

    return (
        <IndexLayout/>
    );
};

export default AppBase;
