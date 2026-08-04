import {App, Button, Space, Tag, Typography} from "antd";
import Alert from "antd/es/alert";
import {marked} from "marked";
import {formatText, getRes} from "utils/constants";
import {useState} from "react";

type UpgradeProgress = {
    stage: string;
    status: "running" | "complete" | "error" | "manual";
    message: string;
    detail?: string;
};

const mergeProgress = (events: UpgradeProgress[], event: UpgradeProgress) => {
    const index = events.findIndex((item) => item.stage === event.stage);
    if (index < 0) {
        return [...events, event];
    }
    return events.map((item, itemIndex) => itemIndex === index ? event : item);
};

const UpgradeButton = () => {

    const {message, modal} = App.useApp();
    const [upgrading, setUpgrading] = useState(false);
    const [progressEvents, setProgressEvents] = useState<UpgradeProgress[]>([]);
    const [upgradeError, setUpgradeError] = useState<string>();
    const res = getRes();
    const upgradeVersion = res.upgradeVersion;

    if (!upgradeVersion) {
        return <></>
    }

    const startUpgrade = async () => {
        setUpgrading(true);
        setUpgradeError(undefined);
        setProgressEvents([]);
        let restartSubmitted = false;
        try {
            const response = await fetch("/api/install/startUpgrade", {
                method: "POST",
                headers: {Accept: "text/event-stream"},
            });
            if (!response.ok || !response.body) {
                throw new Error(`${res.upgrade.failed}: ${response.status}`);
            }
            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = "";
            for (;;) {
                const {done, value} = await reader.read();
                buffer += done ? decoder.decode() : decoder.decode(value, {stream: true});
                const chunks = buffer.split("\n\n");
                buffer = done ? "" : chunks.pop() || "";
                for (const chunk of chunks) {
                    const eventName = chunk.split("\n").find((line) => line.startsWith("event:"))
                        ?.substring("event:".length).trim();
                    const dataText = chunk.split("\n").filter((line) => line.startsWith("data:"))
                        .map((line) => line.substring("data:".length).trim()).join("\n");
                    if (!eventName || !dataText) {
                        continue;
                    }
                    const data = JSON.parse(dataText);
                    if (eventName === "upgrade-progress") {
                        const progress = data as UpgradeProgress;
                        restartSubmitted = restartSubmitted ||
                            (progress.stage === "complete" && progress.status === "complete");
                        setProgressEvents((events) => mergeProgress(events, progress));
                    } else if (eventName === "upgrade-error") {
                        throw new Error(data.message || res.upgrade.failed);
                    } else if (eventName === "upgrade-complete") {
                        restartSubmitted = true;
                    }
                }
                if (done) {
                    break;
                }
            }
            message.success(res.upgrade.complete);
            window.setTimeout(() => window.location.reload(), 1800);
        } catch (error: any) {
            if (restartSubmitted) {
                message.success(res.upgrade.complete);
                window.setTimeout(() => window.location.reload(), 1800);
                return;
            }
            setUpgradeError(error?.message || res.upgrade.failed);
            setUpgrading(false);
        }
    };

    const confirmUpgrade = () => {
        modal.confirm({
            title: res.upgrade.confirmTitle,
            content: res.upgrade.confirmContent,
            okText: res.common.confirm,
            onOk: () => {
                void startUpgrade();
            },
        });
    };

    const progressDescription = (progressEvents.length > 0 || upgradeError) ? <Space direction="vertical" size={4}>
        {progressEvents.map((event) => <Space key={event.stage} size={6} wrap>
            <Tag color={event.status === "complete" ? "success" : event.status === "error" ? "error" : "processing"}
                 style={{marginInlineEnd: 0}}>{event.stage}</Tag>
            <span>{event.message}{event.detail ? ` ${event.detail}` : ""}</span>
        </Space>)}
        {upgradeError && <Typography.Text type="danger">{upgradeError}</Typography.Text>}
    </Space> : undefined;

    return <Alert type={upgradeError ? "error" : "info"}
                  action={
                      <Space.Compact>
                          <Button size={"small"} type="default" onClick={() => {
                              modal.info({
                                  width: 682,
                                  title: res.upgrade.newVersion,
                                  content: <Typography
                                      dangerouslySetInnerHTML={{__html: marked(res.upgradeChangeLog || "") as string}}/>,
                              })
                          }}>
                              {res.common.detail}
                          </Button>
                          {res.onlineUpgradable ? <Button size="small" type="primary" loading={upgrading}
                                                           onClick={confirmUpgrade}>
                              {upgrading ? res.upgrade.upgrading : res.upgrade.action}
                          </Button> : <Button size="small" type="primary" href={res.upgradeDownloadUrl}>
                              {res.common.download}
                          </Button>}
                      </Space.Compact>
                  }
                  message={<div
                      dangerouslySetInnerHTML={{__html: formatText(res.upgrade.newVersionTip, {version: upgradeVersion})}}/>}
                  description={progressDescription}
                  showIcon/>
}

export default UpgradeButton;
