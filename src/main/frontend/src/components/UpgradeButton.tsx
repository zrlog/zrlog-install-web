import {App, Alert, Button, Collapse, Space, Tag, Typography} from "antd";
import {formatText, getRes} from "utils/constants";
import {renderSanitizedMarkdown} from "utils/sanitize-html";
import {useState} from "react";
import {isUpgradeCompletedEvent, isUpgradeEventStream, resolveUpgradeStageKey} from "./upgrade-sse";
import {
    installApiUrl,
    installMutationHeaders,
    installTokenForRequest,
    isRequiredInstallTokenMissing,
} from "../utils/install-api";

type UpgradeProgress = {
    stage: string;
    status: "running" | "complete" | "error" | "manual";
};

type UpgradeButtonProps = {
    compact?: boolean;
    installToken: string;
};

const mergeProgress = (events: UpgradeProgress[], event: UpgradeProgress) => {
    const index = events.findIndex((item) => item.stage === event.stage);
    if (index < 0) {
        return [...events, event];
    }
    return events.map((item, itemIndex) => itemIndex === index ? event : item);
};

const getSafeDownloadUrl = (downloadUrl?: string) => {
    if (!downloadUrl) {
        return undefined;
    }
    try {
        const url = new URL(downloadUrl, document.baseURI);
        return url.protocol === "http:" || url.protocol === "https:" ? url.toString() : undefined;
    } catch (error) {
        return undefined;
    }
};

const UpgradeButton = ({compact = false, installToken}: UpgradeButtonProps) => {
    const {message, modal} = App.useApp();
    const [upgrading, setUpgrading] = useState(false);
    const [progressExpanded, setProgressExpanded] = useState(false);
    const [progressEvents, setProgressEvents] = useState<UpgradeProgress[]>([]);
    const [upgradeError, setUpgradeError] = useState<string>();
    const res = getRes();
    const upgradeVersion = res.upgradeVersion;
    const installTokenRequired = res.installTokenRequired;
    const requestInstallToken = installTokenForRequest(installTokenRequired, installToken);

    if (!upgradeVersion) {
        return <></>;
    }

    const startUpgrade = async () => {
        setUpgrading(true);
        setProgressExpanded(true);
        setUpgradeError(undefined);
        setProgressEvents([]);
        let terminalCompleted = false;
        let terminalErrorReceived = false;
        try {
            const response = await fetch(installApiUrl("startUpgrade"), {
                method: "POST",
                headers: installMutationHeaders(requestInstallToken, "text/event-stream"),
                body: "{}",
            });
            if (!response.ok || !response.body) {
                throw new Error(`${res.upgrade.failed}: ${response.status}`);
            }
            const contentType = response.headers.get("content-type");
            if (!isUpgradeEventStream(contentType)) {
                throw new Error(`${res.upgrade.failed}: ${res.error.requestError}`);
            }
            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = "";
            for (;;) {
                const {done, value} = await reader.read();
                buffer += done ? decoder.decode() : decoder.decode(value, {stream: true});
                buffer = buffer.replace(/\r\n/g, "\n");
                const chunks = buffer.split("\n\n");
                buffer = done ? "" : chunks.pop() || "";
                for (const chunk of chunks) {
                    const lines = chunk.split("\n");
                    const eventName = lines.find((line) => line.startsWith("event:"))
                        ?.substring("event:".length).trim();
                    const dataText = lines.filter((line) => line.startsWith("data:"))
                        .map((line) => line.substring("data:".length).trim()).join("\n");
                    if (!eventName || !dataText) {
                        continue;
                    }
                    const data = JSON.parse(dataText);
                    if (eventName === "upgrade-progress") {
                        const progress = data as UpgradeProgress;
                        terminalCompleted = terminalCompleted || isUpgradeCompletedEvent(eventName, progress);
                        setProgressEvents((events) => mergeProgress(events, progress));
                    } else if (eventName === "upgrade-error") {
                        terminalErrorReceived = true;
                        throw new Error(res.upgrade.failed);
                    } else if (eventName === "upgrade-complete") {
                        terminalCompleted = isUpgradeCompletedEvent(eventName, data);
                    }
                }
                if (done) {
                    break;
                }
            }
            if (!terminalCompleted) {
                throw new Error(`${res.upgrade.failed}: ${res.error.requestError}`);
            }
            message.success(res.upgrade.complete);
            window.setTimeout(() => window.location.reload(), 1800);
        } catch {
            if (terminalCompleted && !terminalErrorReceived) {
                message.success(res.upgrade.complete);
                window.setTimeout(() => window.location.reload(), 1800);
                return;
            }
            setUpgradeError(res.upgrade.failed);
            setProgressExpanded(true);
            setUpgrading(false);
        }
    };

    const confirmUpgrade = () => {
        if (isRequiredInstallTokenMissing(installTokenRequired, installToken)) {
            message.error(res.security.tokenRequired);
            return;
        }
        modal.confirm({
            title: res.upgrade.confirmTitle,
            content: res.upgrade.confirmContent,
            okText: res.common.confirm,
            onOk: () => {
                void startUpgrade();
            },
        });
    };

    const showChangeLog = () => {
        const changeLog = renderSanitizedMarkdown(res.upgradeChangeLog || "");
        modal.info({
            width: 682,
            title: res.upgrade.newVersion,
            content: <Typography className="install-disclaimer-content"
                                 dangerouslySetInnerHTML={{__html: changeLog}}/>,
            okText: res.common.close,
        });
    };

    const progressDetails = (upgrading || progressEvents.length > 0 || upgradeError) ?
        <Space direction="vertical" size={4} style={{width: "100%"}}>
            {upgrading && progressEvents.length === 0 &&
                <Typography.Text type="secondary">{res.upgrade.upgrading}</Typography.Text>}
            {progressEvents.map((event) => <Space key={event.stage} size={6} wrap>
                <Tag color={event.status === "complete" ? "success" : event.status === "error" ? "error" : "processing"}
                     style={{marginInlineEnd: 0}}>
                    {event.status === "complete" ? res.progress.complete :
                        event.status === "error" ? res.progress.error : res.progress.running}
                </Tag>
                <span>{res.upgrade.stage[resolveUpgradeStageKey(event.stage)]}</span>
            </Space>)}
            {upgradeError && <Typography.Text type="danger">{upgradeError}</Typography.Text>}
        </Space> : undefined;

    const progressPanel = progressDetails ? <Collapse
        className="install-upgrade-progress"
        size="small"
        activeKey={progressExpanded ? ["progress"] : []}
        onChange={(keys) => setProgressExpanded((Array.isArray(keys) ? keys : [keys]).includes("progress"))}
        items={[{
            key: "progress",
            label: res.upgrade.progressTitle,
            children: progressDetails,
        }]}
    /> : undefined;

    const downloadUrl = getSafeDownloadUrl(res.upgradeDownloadUrl);
    const actionType = compact ? "link" : "primary";
    const actions = <Space.Compact>
        <Button size="small" type={compact ? "link" : "default"} onClick={showChangeLog}>
            {res.common.detail}
        </Button>
        {res.onlineUpgradable ? <Button size="small" type={actionType} loading={upgrading}
                                               onClick={confirmUpgrade}>
            {upgrading ? res.upgrade.upgrading : res.upgrade.action}
        </Button> : <Button size="small" type={actionType} href={downloadUrl} disabled={!downloadUrl}>
            {res.common.download}
        </Button>}
    </Space.Compact>;

    const messageText = upgradeError ? res.upgrade.failed :
        formatText(res.upgrade.newVersionTip, {version: upgradeVersion});
    if (compact) {
        return <div className="install-upgrade">
            <Typography.Text type={upgradeError ? "danger" : "secondary"}>{messageText}</Typography.Text>
            {actions}
            {progressPanel}
        </div>;
    }

    return <Alert type={upgradeError ? "error" : "info"}
                  action={actions}
                  message={messageText}
                  description={progressPanel}
                  showIcon/>;
};

export default UpgradeButton;
