import {useEffect, useRef, useState} from 'react';
import {
    Alert,
    App,
    Button,
    Checkbox,
    Collapse,
    Form,
    Input,
    InputNumber,
    Layout,
    List,
    message,
    Progress,
    Radio,
    Result,
    Segmented,
    Space,
    Steps,
    Tag,
    Tooltip,
    Typography
} from 'antd';
import type {FormInstance, InputRef, RadioProps} from 'antd';
import {
    ArrowLeftOutlined,
    ArrowRightOutlined,
    CheckCircleFilled,
    CloudServerOutlined,
    DatabaseOutlined,
    DesktopOutlined,
    GlobalOutlined,
    LoadingOutlined,
    LockOutlined,
    MoonOutlined,
    QuestionCircleOutlined,
    ReloadOutlined,
    SafetyCertificateOutlined,
    SunOutlined,
    ThunderboltOutlined,
    UserOutlined,
} from "@ant-design/icons";

import axios from "axios";
import Text from "antd/es/typography/Text";
import {formatText, getRes, InstallRuntimeResourceInfo} from "../utils/constants";
import DisclaimerAgreement from "./DisclaimerAgreement";
import UpgradeButton from './UpgradeButton';
import InstallSuccessContent from './InstallSuccessContent';
import InstallRecoveryPanel from './InstallRecoveryPanel';
import EnvUtils, {ThemeMode} from "../utils/env-utils";
import {sanitizeRichHtml} from "../utils/sanitize-html";
import InstallBrand from "./InstallBrand";
import {
    installApiUrl,
    installMutationHeaders,
    installTokenForRequest,
    isRequiredInstallTokenMissing,
    readInstallToken,
    saveInstallToken,
} from "../utils/install-api";
import {InstallApiError, resolveInstallDbErrorText} from "../utils/install-errors";
import {
    InstallRuntimeView,
    resolveInstallRuntimeView,
    shouldShowConfigurationHandoff,
} from "../utils/install-runtime-state";
import {extractInstallRuntimeResource} from "../utils/install-resource-response";
import {
    extractInstallProbeData,
    InstallProbeData,
    InstallProbeItem,
} from "../utils/install-probe-response";
import "../install.css";

const FormItem = Form.Item;
const {Title, Paragraph} = Typography;

const installModeRadioStyles: NonNullable<RadioProps["styles"]> = {
    root: {alignItems: "flex-start"},
    icon: {alignSelf: "flex-start", marginBlockStart: 2},
    label: {minWidth: 0, flex: 1},
};

type ProgressStatus = "running" | "complete" | "error";

type InstallProgressEvent = {
    code: string;
    status: ProgressStatus;
    detail?: string;
};

type SseEvent = {
    event: string;
    data: any;
};

type AppState = {
    current: number;
    installed: boolean;
    testConnecting: boolean;
    installing: boolean;
    agreementAccepted: boolean;
    probeLoading: boolean;
    probe?: InstallProbeData;
    probeError?: string;
    dbError?: InstallApiError;
    installError?: InstallProgressEvent;
    progressEvents: InstallProgressEvent[];
    dataBaseInfo: Record<string, string | number>;
    weblogInfo: Record<string, string | number>;
    installSuccessContent: string;
    configurationRequired: boolean;
}

const getDefaultPort = (dbType: string): number => {
    if (dbType === "webapi") {
        return 443;
    }
    if (dbType === "mysql") {
        return 3306;
    }
    return 0;
}

const mergeProgressEvent = (events: InstallProgressEvent[], event: InstallProgressEvent): InstallProgressEvent[] => {
    const index = events.findIndex((item) => item.code === event.code);
    if (index < 0) {
        return [...events, event];
    }
    return events.map((item, itemIndex) => itemIndex === index ? event : item);
};

const parseSseEvent = (chunk: string): SseEvent | null => {
    const lines = chunk.replace(/\r\n/g, "\n").split("\n");
    const event = lines.find((line) => line.startsWith("event:"))?.substring("event:".length).trim();
    const data = lines
        .filter((line) => line.startsWith("data:"))
        .map((line) => line.substring("data:".length).trim())
        .join("\n");
    if (!event || !data) {
        return null;
    }
    return {
        event,
        data: JSON.parse(data),
    };
};

const getDbErrorText = (error?: InstallApiError) => {
    if (!error) {
        return "";
    }
    const res = getRes();
    return resolveInstallDbErrorText(error, res.error.db) || res.error.db.UNKNOWN;
};

const getProbeItemDescription = (item: InstallProbeItem) => {
    const res = getRes();
    const itemText = res.probe.item[item.code as keyof typeof res.probe.item];
    if (!itemText) {
        return res.probe.unknownItem;
    }
    const template = itemText[item.status];
    return formatText(template, {value: item.value});
};

const getProgressText = (event: InstallProgressEvent) => {
    const res = getRes();
    if (event.status === "error") {
        const dbErrorText = resolveInstallDbErrorText({code: event.code, message: event.detail}, res.error.db);
        if (dbErrorText) {
            return dbErrorText;
        }
    }
    const progressText = res.progress.item[event.code as keyof typeof res.progress.item] || res.progress.item.install;
    if (event.status === "error") {
        return `${progressText}: ${res.error.requestError}`;
    }
    return progressText;
};

const progressStepCodes = ["preflight", "database", "schema", "seed-website", "seed-admin", "seed-defaults", "config"];

const getInitialDatabaseInfo = (localSqliteAvailable: boolean): Record<string, string | number> => {
    const dbType = localSqliteAvailable ? "sqlite" : "mysql";
    return {
        dbType,
        dbPort: getDefaultPort(dbType),
    };
};

const getProgressColor = (status: ProgressStatus) => {
    if (status === "complete") {
        return "success";
    }
    if (status === "error") {
        return "error";
    }
    return "processing";
};

const getProgressStatusText = (status: ProgressStatus) => {
    const res = getRes();
    if (status === "running") {
        return res.progress.running;
    }
    if (status === "complete") {
        return res.progress.complete;
    }
    return res.progress.error;
};

const IndexLayout = () => {
    const res = getRes();
    const installTokenRequired = res.installTokenRequired;
    const [state, setState] = useState<AppState>({
        current: 0,
        installed: res.installed === true,
        configurationRequired: shouldShowConfigurationHandoff(res),
        testConnecting: false,
        agreementAccepted: false,
        probeLoading: false,
        installSuccessContent: res.installSuccessContent || "",
        installing: false,
        progressEvents: [],
        dataBaseInfo: getInitialDatabaseInfo(res.localSqliteAvailable === true),
        weblogInfo: {username: "admin"},
    });

    const formDataBaseInfoRef = useRef<FormInstance>(null);
    const formWeblogInfoRef = useRef<FormInstance>(null);
    const installTokenInputRef = useRef<InputRef>(null);
    const testingConnectionRef = useRef(false);
    const installingRef = useRef(false);
    const runtimeRefreshInFlightRef = useRef<Promise<InstallRuntimeView | undefined> | null>(null);
    const [messageApi, contextHolder] = message.useMessage({maxCount: 3});
    const {modal} = App.useApp();
    const [themeMode, setThemeMode] = useState<ThemeMode>(EnvUtils.getThemeMode());
    const [installToken, setInstallToken] = useState(() => installTokenRequired ? readInstallToken() : "");
    const [installTokenTouched, setInstallTokenTouched] = useState(false);
    const installTokenInvalid = installTokenTouched &&
        isRequiredInstallTokenMissing(installTokenRequired, installToken);
    const [recoveryVisible, setRecoveryVisible] = useState(res.installRecoveryAvailable === true);
    const [installOperationInProgress, setInstallOperationInProgress] = useState(
        res.installOperationInProgress === true,
    );

    useEffect(() => {
        if (!installTokenRequired) {
            saveInstallToken("");
        }
    }, [installTokenRequired]);

    useEffect(() => {
        const changeHandler = () => setThemeMode(EnvUtils.getThemeMode());
        window.addEventListener(EnvUtils.themeModeChangeEvent, changeHandler);
        window.addEventListener("storage", changeHandler);
        return () => {
            window.removeEventListener(EnvUtils.themeModeChangeEvent, changeHandler);
            window.removeEventListener("storage", changeHandler);
        };
    }, []);

    const changeThemeMode = (nextThemeMode: ThemeMode) => {
        setThemeMode(nextThemeMode);
        EnvUtils.setThemeMode(nextThemeMode);
    };

    const changeInstallToken = (nextInstallToken: string) => {
        setInstallToken(nextInstallToken);
        saveInstallToken(nextInstallToken);
        if (nextInstallToken.trim()) {
            setInstallTokenTouched(false);
        }
    };

    const applyInstallRuntimeState = (runtime: InstallRuntimeResourceInfo): InstallRuntimeView => {
        const runtimeView = resolveInstallRuntimeView(runtime);
        setInstallOperationInProgress(runtimeView === "in-progress");
        setRecoveryVisible(runtimeView === "recoverable");
        if (runtimeView === "installed") {
            installingRef.current = false;
            setState((prevState) => ({
                ...prevState,
                installed: true,
                configurationRequired: shouldShowConfigurationHandoff(runtime),
                installing: false,
                installError: undefined,
                installSuccessContent: runtime.installSuccessContent || prevState.installSuccessContent,
            }));
        }
        return runtimeView;
    };

    const refreshInstallRuntimeState = (): Promise<InstallRuntimeView | undefined> => {
        if (runtimeRefreshInFlightRef.current) {
            return runtimeRefreshInFlightRef.current;
        }
        const refreshPromise: Promise<InstallRuntimeView | undefined> = Promise.resolve()
            .then(() => axios.get(installApiUrl("installResource")))
            .then(({data}: {data: unknown}) => {
                const runtime = extractInstallRuntimeResource(data);
                return runtime === undefined ? undefined : applyInstallRuntimeState(runtime);
            })
            .catch(() => {
                // Preserve the current view until the next explicit or automatic status check.
                return undefined;
            })
            .finally(() => {
                runtimeRefreshInFlightRef.current = null;
            });
        runtimeRefreshInFlightRef.current = refreshPromise;
        return refreshPromise;
    };

    useEffect(() => {
        if (!installOperationInProgress) {
            return;
        }
        let cancelled = false;
        let timeout: number | undefined;
        const poll = async () => {
            const runtimeView = await refreshInstallRuntimeState();
            if (!cancelled && (runtimeView === "in-progress" || runtimeView === undefined)) {
                timeout = window.setTimeout(() => void poll(), 1500);
            }
        };
        timeout = window.setTimeout(() => void poll(), 1500);
        return () => {
            cancelled = true;
            if (timeout !== undefined) {
                window.clearTimeout(timeout);
            }
        };
    }, [installOperationInProgress]);

    const loadProbe = () => {
        setState((prevState) => ({...prevState, probeLoading: true, probeError: undefined}));
        axios.get(installApiUrl("probe")).then(({data}: {data: unknown}) => {
            const probe = extractInstallProbeData(data);
            if (probe === undefined) {
                setState((prevState) => ({
                    ...prevState,
                    probe: undefined,
                    probeLoading: false,
                    probeError: res.error.requestError,
                }));
                return;
            }
            setState((prevState) => ({
                ...prevState,
                probe,
                probeLoading: false,
                probeError: undefined,
            }));
        }).catch(() => {
            setState((prevState) => ({
                ...prevState,
                probeLoading: false,
                probeError: res.probe.unavailable,
            }));
        });
    };

    useEffect(() => {
        if (!state.installed && state.current === 0 && !recoveryVisible) {
            loadProbe();
        }
    }, [recoveryVisible]);

    useEffect(() => {
        if (installTokenInvalid) {
            installTokenInputRef.current?.focus();
        }
    }, [installTokenInvalid]);

    useEffect(() => {
        if (state.current !== 1 || !formWeblogInfoRef.current) {
            return;
        }
        if (!formWeblogInfoRef.current.getFieldValue("username")) {
            formWeblogInfoRef.current.setFieldValue("username", "admin");
        }
    }, [state.current]);

    const getSteps = () => {
        return [
            {
                title: res.wizard.databaseStep,
                description: res.wizard.databaseStepDescription,
            }, {
                title: res.wizard.websiteStep,
                description: res.wizard.websiteStepDescription,
            }, {
                title: res.wizard.completeStep,
                description: res.wizard.completeStepDescription,
            }
        ]
    }

    const setDatabaseValue = (changedValues: Record<string, string | number>, allValues: Record<string, string | number>) => {
        if (!formDataBaseInfoRef.current) {
            return;
        }
        if (changedValues.dbType !== undefined) {
            const dbType = changedValues.dbType as string;
            const nextValues = {dbType, dbPort: getDefaultPort(dbType)};
            formDataBaseInfoRef.current.setFieldsValue({
                dbHost: undefined,
                dbName: undefined,
                dbUserName: undefined,
                dbPassword: undefined,
                ...nextValues,
            });
            setState((prevState) => ({
                ...prevState,
                dataBaseInfo: nextValues,
                dbError: undefined,
            }));
            return;
        }
        setState((prevState) => ({
            ...prevState,
            dataBaseInfo: {...allValues},
            dbError: undefined,
        }));
    }

    const setWeblogValue = (changedValues: Record<string, string | number>, allValues: Record<string, string | number>) => {
        if (!formWeblogInfoRef.current) {
            return;
        }
        formWeblogInfoRef.current.setFieldsValue(changedValues);
        setState((prevState) => ({
            ...prevState,
            weblogInfo: allValues,
        }));
    }

    const nextFromDatabase = async () => {
        if (testingConnectionRef.current || !state.agreementAccepted || state.probeLoading ||
            !state.probe || state.probe.status === "block") {
            return;
        }
        const requestInstallToken = installTokenForRequest(installTokenRequired, installToken);
        if (isRequiredInstallTokenMissing(installTokenRequired, installToken)) {
            setInstallTokenTouched(true);
            return;
        }
        testingConnectionRef.current = true;
        let dataBaseInfo: Record<string, string | number>;
        try {
            dataBaseInfo = await formDataBaseInfoRef.current?.validateFields() || state.dataBaseInfo;
        } catch (error) {
            testingConnectionRef.current = false;
            return;
        }
        setState((prevState) => ({...prevState, testConnecting: true, dbError: undefined}));
        try {
            const {data} = await axios.post(installApiUrl("testDbConn"), dataBaseInfo, {
                headers: installMutationHeaders(requestInstallToken),
            });
            if (!data.error) {
                setState((prevState) => ({...prevState, current: 1, dataBaseInfo}));
                return;
            }
            setState((prevState) => ({
                ...prevState,
                dbError: {code: data.code, message: data.message},
            }));
        } catch (error: any) {
            setState((prevState) => ({
                ...prevState,
                dbError: {
                    code: error?.response?.data?.code,
                    message: error?.response?.data?.message || error?.message || res.error.requestError,
                },
            }));
        } finally {
            testingConnectionRef.current = false;
            setState((prevState) => ({...prevState, testConnecting: false}));
        }
    };

    const handleInstallSseEvent = (event: SseEvent) => {
        if (event.event === "install-progress") {
            setState((prevState) => ({
                ...prevState,
                progressEvents: mergeProgressEvent(prevState.progressEvents, event.data),
            }));
            return;
        }
        if (event.event === "install-error") {
            installingRef.current = false;
            setState((prevState) => ({
                ...prevState,
                installing: false,
                installError: event.data,
                progressEvents: mergeProgressEvent(prevState.progressEvents, event.data),
            }));
            void refreshInstallRuntimeState();
            return;
        }
        if (event.event === "install-complete") {
            installingRef.current = false;
            setState((prevState) => ({
                ...prevState,
                current: 2,
                installing: false,
                installSuccessContent: event.data?.data?.content || "",
            }));
        }
    };

    const startInstall = async () => {
        if (installingRef.current) {
            return;
        }
        const requestInstallToken = installTokenForRequest(installTokenRequired, installToken);
        if (isRequiredInstallTokenMissing(installTokenRequired, installToken)) {
            messageApi.error(res.security.tokenRequired);
            return;
        }
        installingRef.current = true;
        let weblogInfo: Record<string, string | number>;
        try {
            weblogInfo = await formWeblogInfoRef.current?.validateFields() || state.weblogInfo;
        } catch (error) {
            installingRef.current = false;
            return;
        }
        setState((prevState) => ({
            ...prevState,
            installing: true,
            installError: undefined,
            progressEvents: [],
            weblogInfo,
        }));
        let response: Response;
        const weblogRequest = {...weblogInfo};
        delete weblogRequest.confirmPassword;
        const installRequest = {
            ...state.dataBaseInfo,
            ...weblogRequest,
        };
        try {
            response = await fetch(installApiUrl("startInstall"), {
                method: "POST",
                headers: installMutationHeaders(requestInstallToken, "text/event-stream"),
                body: JSON.stringify(installRequest),
            });
        } catch {
            installingRef.current = false;
            messageApi.error(res.error.requestError);
            setState((prevState) => ({...prevState, installing: false}));
            void refreshInstallRuntimeState();
            return;
        }
        if (!response.ok) {
            let code = "install";
            let detail = `${res.error.requestError}: ${response.status}`;
            try {
                const data = await response.json();
                code = data.code || code;
                detail = data.message || detail;
            } catch (error) {
                // Keep the status-based fallback when the server has no JSON body.
            }
            installingRef.current = false;
            setState((prevState) => ({
                ...prevState,
                installing: false,
                installError: {code, status: "error", detail},
            }));
            void refreshInstallRuntimeState();
            return;
        }
        const contentType = response.headers.get("content-type");
        if (!contentType || !contentType.includes("text/event-stream")) {
            let data: any;
            try {
                data = await response.json();
            } catch (error) {
                installingRef.current = false;
                setState((prevState) => ({
                    ...prevState,
                    installing: false,
                    installError: {
                        code: "install",
                        status: "error",
                        detail: res.error.requestError,
                    },
                }));
                void refreshInstallRuntimeState();
                return;
            }
            if (!data.error) {
                installingRef.current = false;
                setState((prevState) => ({
                    ...prevState,
                    current: 2,
                    installing: false,
                    installSuccessContent: data.data?.content || "",
                }));
                return;
            }
            installingRef.current = false;
            setState((prevState) => ({
                ...prevState,
                installing: false,
                installError: {code: data.code || "install", status: "error", detail: data.message},
            }));
            return;
        }
        const reader = response.body?.getReader();
        if (!reader) {
            installingRef.current = false;
            messageApi.error(res.error.requestError);
            setState((prevState) => ({...prevState, installing: false}));
            void refreshInstallRuntimeState();
            return;
        }
        const decoder = new TextDecoder();
        let buffer = "";
        let terminalEventReceived = false;
        try {
            for (;;) {
                const {done, value} = await reader.read();
                buffer += done ? decoder.decode() : decoder.decode(value, {stream: true});
                buffer = buffer.replace(/\r\n/g, "\n");
                const chunks = buffer.split("\n\n");
                buffer = done ? "" : chunks.pop() || "";
                for (const chunk of chunks) {
                    const event = parseSseEvent(chunk);
                    if (event) {
                        terminalEventReceived = terminalEventReceived ||
                            event.event === "install-complete" || event.event === "install-error";
                        handleInstallSseEvent(event);
                    }
                }
                if (done) {
                    break;
                }
            }
            if (!terminalEventReceived) {
                throw new Error(res.progress.disconnected);
            }
        } catch {
            installingRef.current = false;
            setState((prevState) => ({
                ...prevState,
                installing: false,
                installError: {
                    code: "install",
                    status: "error",
                    detail: res.error.requestError,
                },
            }));
            void refreshInstallRuntimeState();
        }
    };

    const prev = () => {
        setState((prevState) => ({...prevState, current: prevState.current - 1}));
    }

    const renderProbe = () => {
        if (state.probeLoading) {
            return <Alert type="info" showIcon message={res.probe.checking}/>;
        }
        if (state.probeError) {
            return <Alert type="error" showIcon message={state.probeError} action={<Button size="small" onClick={loadProbe}>{res.common.retry}</Button>}/>;
        }
        if (!state.probe) {
            return <></>;
        }
        const alertType = state.probe.status === "pass" ? "success" : state.probe.status === "warning" ? "warning" : "error";
        const messageText = state.probe.status === "pass" ? res.probe.pass : state.probe.status === "warning" ? res.probe.warning : res.probe.block;
        const shouldShowDetails = state.probe.status !== "pass";
        if (!shouldShowDetails) {
            return <div className="install-probe-pass" role="status" aria-live="polite">
                <CheckCircleFilled aria-hidden="true" style={{color: "var(--ant-color-success)"}}/>
                <Text>{messageText}</Text>
                <Text type="secondary">{res.probe.title}</Text>
                <Button type="link" size="small" onClick={loadProbe}>{res.probe.retry}</Button>
            </div>;
        }
        return <Space className="install-probe-details" direction="vertical">
            <Alert
                type={alertType}
                showIcon
                message={messageText}
                action={<Button size="small" onClick={loadProbe}>{res.probe.retry}</Button>}
            />
            {shouldShowDetails && (
                <Collapse
                    size="small"
                    items={[{
                        key: "probe",
                        label: res.probe.expand,
                        children: <List className="install-probe-list"
                            size="small"
                            dataSource={state.probe.items}
                            renderItem={(item) => (
                                <List.Item>
                                    <Space direction="vertical" size={2}>
                                        <Space>
                                            <Tag color={item.status === "pass" ? "green" : item.status === "warning" ? "gold" : "red"}>
                                                {res.probe[item.status]}
                                            </Tag>
                                            <Text strong>{res.probe.item[item.code as keyof typeof res.probe.item]?.title ||
                                                res.probe.unknownItem}</Text>
                                        </Space>
                                        <Text type={item.status === "block" ? "danger" : "secondary"}>{getProbeItemDescription(item)}</Text>
                                    </Space>
                                </List.Item>
                            )}
                        />
                    }]}
                />
            )}
        </Space>;
    };

    const renderProgress = () => {
        if (!state.installing && state.progressEvents.length === 0 && !state.installError) {
            return <></>;
        }
        const currentEvent = state.installError ||
            state.progressEvents.find((event) => event.status === "running") ||
            state.progressEvents[state.progressEvents.length - 1];
        const completedCount = state.progressEvents.filter((event) => event.status === "complete").length;
        return <div className="install-progress-panel" role="status" aria-live="polite">
            <Space direction="vertical" size={6} style={{width: "100%"}}>
                <div className="install-progress-current">
                    <Text strong>{res.progress.title}</Text>
                    {currentEvent && <Tag color={getProgressColor(currentEvent.status)} style={{marginInlineEnd: 0}}>
                        {getProgressStatusText(currentEvent.status)}
                    </Tag>}
                    {currentEvent && <Text type={currentEvent.status === "error" ? "danger" : "secondary"}>
                        {getProgressText(currentEvent)}
                    </Text>}
                    {!state.installError && <Text type="secondary">{completedCount}/{progressStepCodes.length}</Text>}
                </div>
                {state.progressEvents.length > 0 && (
                    <div className="install-progress-history">
                        {state.progressEvents.map((event) => (
                            <Tag key={event.code} color={getProgressColor(event.status)} style={{marginInlineEnd: 0}}>
                                {getProgressText(event)}
                            </Tag>
                        ))}
                    </div>
                )}
            </Space>
        </div>
    };

    const showFeedback = state.current <= 1;
    const nextDisabled = state.testConnecting || !state.agreementAccepted || state.probeLoading || !state.probe || state.probe.status === "block";
    const currentDbType = state.dataBaseInfo.dbType as string;
    const mysqlSelected = currentDbType === "mysql";
    const sqliteSelected = currentDbType === "sqlite";
    const localSqliteAvailable = res.localSqliteAvailable === true;
    const steps = getSteps();
    const currentStep = steps[state.current] || steps[0];
    const themeSwitcher = (
        <Tooltip title={res.theme.title}>
            <Segmented
                className="install-theme-switcher"
                size="small"
                value={themeMode}
                aria-label={res.theme.title}
                onChange={(value) => changeThemeMode(value as ThemeMode)}
                options={[
                    {
                        icon: <SunOutlined aria-hidden="true"/>,
                        label: <span className="install-theme-label">{res.theme.light}</span>,
                        value: "light",
                    },
                    {
                        icon: <MoonOutlined aria-hidden="true"/>,
                        label: <span className="install-theme-label">{res.theme.dark}</span>,
                        value: "dark",
                    },
                    {
                        icon: <DesktopOutlined aria-hidden="true"/>,
                        label: <span className="install-theme-label">{res.theme.system}</span>,
                        value: "system",
                    },
                ]}
            />
        </Tooltip>
    );
    const brandHeader = <header className="install-header" role="banner">
        <InstallBrand/>
        {themeSwitcher}
    </header>;

    const showDisclaimer = () => {
        modal.info({
            width: 720,
            title: res.agreement.title,
            content: <DisclaimerAgreement/>,
            okText: res.common.close,
        });
    };

    const completeRecovery = (content: string) => {
        setRecoveryVisible(false);
        setState((prevState) => ({
            ...prevState,
            current: 2,
            installing: false,
            installError: undefined,
            installSuccessContent: content,
        }));
    };

    const startNewInstall = () => {
        installingRef.current = false;
        setRecoveryVisible(false);
        setInstallOperationInProgress(false);
        setState((prevState) => ({
            ...prevState,
            current: 0,
            installing: false,
            dbError: undefined,
            installError: undefined,
            progressEvents: [],
        }));
    };

    if (state.installed) {
        return <Layout className="install-shell">
            <div className="install-page">
                {brandHeader}
                <main className="install-main">
                    {state.configurationRequired ?
                        <InstallSuccessContent content={state.installSuccessContent}
                                               configurationRequired={state.configurationRequired}
                                               installToken={installToken}
                                               onInstallTokenChange={changeInstallToken}/> :
                        <Result className="install-installed-result" status="info"
                                title={<Title className="install-result-title" level={1} aria-live="polite">
                                    {res.installedPage.title}
                                </Title>}
                                subTitle={<Paragraph className="install-result-description">
                                    {res.warMode ? res.installedPage.warTips : res.installedPage.tips}
                                </Paragraph>}
                                extra={<Button className="install-result-action" type="primary"
                                               href={document.baseURI}>{res.success.viewSite}</Button>}/>
                    }
                </main>
            </div>
        </Layout>;
    }

    if (installOperationInProgress) {
        return <Layout className="install-shell">
            {contextHolder}
            <div className="install-page install-operation-page">
                {brandHeader}
                <main className="install-recovery-main" aria-live="polite">
                    <Result className="install-operation-result"
                            icon={<LoadingOutlined spin/>}
                            title={<Title className="install-result-title" level={1} aria-live="polite">
                                {res.operation.title}
                            </Title>}
                            subTitle={<Paragraph className="install-result-description">
                                {res.operation.description}
                            </Paragraph>}
                            extra={<Button className="install-result-action"
                                           icon={<ReloadOutlined aria-hidden="true"/>}
                                           onClick={() => void refreshInstallRuntimeState()}>
                                {res.operation.refresh}
                            </Button>}/>
                </main>
            </div>
        </Layout>;
    }

    if (recoveryVisible) {
        return <Layout className="install-shell">
            {contextHolder}
            <div className="install-page install-recovery-page">
                {brandHeader}
                <main className="install-recovery-main">
                    <InstallRecoveryPanel installToken={installToken}
                                          onRecovered={completeRecovery}
                                          onInstallTokenChange={changeInstallToken}
                                          onRuntimeStateChanged={() => void refreshInstallRuntimeState()}
                                          onStartNew={startNewInstall}/>
                </main>
                <aside className="install-support" aria-label={res.feedback.title}>
                    <div className="install-support-group">
                        <QuestionCircleOutlined/>
                        <span>{res.feedback.title}</span>
                        <Button type="link" size="small" target="_blank" rel="noreferrer"
                                href={res.feedbackUrl}>
                            {res.feedback.linkText}
                        </Button>
                    </div>
                </aside>
            </div>
            <footer className="install-footer">
                <span dangerouslySetInnerHTML={{__html: sanitizeRichHtml(res.copyrightTips)}}/>
                {res.copyrightSuffix}
            </footer>
        </Layout>;
    }

    return (
        <Layout className="install-shell">
            {contextHolder}
            <div className="install-page">
                {brandHeader}
                <div className="install-workspace">
                    <aside className="install-rail" aria-label={res.wizard.progressLabel}>
                        <Steps current={state.current} items={steps} direction="vertical" responsive={false}/>
                        {state.current === 0 && renderProbe()}
                    </aside>
                    <main className="install-main">
                        <div className="install-mobile-progress">
                            <div className="install-mobile-progress-copy">
                                <span>{formatText(res.wizard.stepCounter, {
                                    current: state.current + 1,
                                    total: steps.length,
                                })}</span>
                                <strong>{currentStep.title}</strong>
                            </div>
                            <Progress percent={((state.current + 1) / steps.length) * 100}
                                      showInfo={false}
                                      size="small"
                                      aria-label={`${res.wizard.progressLabel}: ${currentStep.title}`}
                                      aria-valuetext={formatText(res.wizard.stepCounter, {
                                          current: state.current + 1,
                                          total: steps.length,
                                      })}/>
                        </div>
                        {state.current === 0 && <div className="install-mobile-probe">{renderProbe()}</div>}
                        <div className="install-content">
                    {state.current === 0 && (
                        <>
                            <div className="install-section-heading">
                                <span className="install-eyebrow">{formatText(res.wizard.stepCounter, {
                                    current: 1,
                                    total: steps.length,
                                })}</span>
                                <Title className="install-section-title" level={1} aria-live="polite">
                                    {res.database.title}
                                </Title>
                                <Paragraph className="install-section-description" type="secondary">
                                    {res.database.description}
                                </Paragraph>
                            </div>
                            {state.dbError && (
                                <Alert
                                    type="error"
                                    showIcon
                                    message={res.database.testFailedTitle}
                                    description={<Space direction="vertical">
                                        <Text>{getDbErrorText(state.dbError)}</Text>
                                        <Text type="secondary">{res.database.testFailedAction}</Text>
                                    </Space>}
                                />
                            )}
                            <Form id="install-database-form" key="database-form" className="install-form"
                                  ref={formDataBaseInfoRef} layout="vertical"
                                  scrollToFirstError={{focus: true}}
                                  initialValues={state.dataBaseInfo} preserve={false}
                                  onFinish={() => void nextFromDatabase()}
                                  onValuesChange={(k: any, v: any) => setDatabaseValue(k, v)}>
                                {installTokenRequired && <FormItem label={res.security.tokenLabel}
                                          htmlFor="install-setup-token"
                                          required
                                          validateStatus={installTokenInvalid ? "error" : undefined}
                                          help={installTokenInvalid ? <span id="install-setup-token-error" role="alert">
                                              {res.security.tokenRequired}
                                          </span> : undefined}
                                          extra={<span id="install-setup-token-help">{res.security.tokenHelp}</span>}>
                                    <Input.Password id="install-setup-token"
                                                    ref={installTokenInputRef}
                                                    value={installToken}
                                                    onChange={(event) => changeInstallToken(event.target.value)}
                                                    placeholder={res.security.tokenPlaceholder}
                                                    autoComplete="off"
                                                    aria-describedby={`install-setup-token-help${installTokenInvalid ? " install-setup-token-error" : ""}`}
                                                    aria-errormessage={installTokenInvalid ? "install-setup-token-error" : undefined}
                                                    aria-required="true"
                                                    aria-invalid={installTokenInvalid}/>
                                </FormItem>}
                                <FormItem className="install-mode-field" name="dbType" label={res.database.modeLabel}
                                          rules={[{required: true}]}>
                                    <Radio.Group className="install-mode-group" aria-label={res.database.modeLabel}>
                                        {localSqliteAvailable && <Radio value="sqlite"
                                            styles={installModeRadioStyles}
                                            className={`install-mode-option ${sqliteSelected ? "is-selected" : ""}`}>
                                            <span className="install-mode-copy">
                                                <span className="install-mode-icon"><ThunderboltOutlined aria-hidden="true"/></span>
                                                <span className="install-mode-text">
                                                    <span className="install-mode-title">
                                                        {res.database.sqliteTitle}
                                                        <Tag color="success">{res.database.recommended}</Tag>
                                                    </span>
                                                    <span className="install-mode-description">{res.database.sqliteDescription}</span>
                                                </span>
                                            </span>
                                        </Radio>}
                                        <Radio value="mysql"
                                               styles={installModeRadioStyles}
                                               className={`install-mode-option ${mysqlSelected ? "is-selected" : ""}`}>
                                            <span className="install-mode-copy">
                                                <span className="install-mode-icon"><DatabaseOutlined aria-hidden="true"/></span>
                                                <span className="install-mode-text">
                                                    <span className="install-mode-title">MySQL / MariaDB</span>
                                                    <span className="install-mode-description">{res.database.mysqlDescription}</span>
                                                </span>
                                            </span>
                                        </Radio>
                                        <Radio value="webapi"
                                               styles={installModeRadioStyles}
                                               className={`install-mode-option ${currentDbType === "webapi" ? "is-selected" : ""}`}>
                                            <span className="install-mode-copy">
                                                <span className="install-mode-icon"><CloudServerOutlined aria-hidden="true"/></span>
                                                <span className="install-mode-text">
                                                    <span className="install-mode-title">WebApi</span>
                                                    <span className="install-mode-description">{res.database.webApiDescription}</span>
                                                </span>
                                            </span>
                                        </Radio>
                                    </Radio.Group>
                                </FormItem>
                                {!sqliteSelected && <div className="install-connection-fields">
                                    <div className="install-fields-heading"><SafetyCertificateOutlined/>
                                        {res.database.connectionTitle}</div>
                                    <div className="install-field-row">
                                        <FormItem name="dbHost" label={res.database.dbHost}
                                                  rules={[{required: true}]}>
                                            <Input prefix={<GlobalOutlined/>} placeholder="127.0.0.1" autoComplete="off"/>
                                        </FormItem>
                                        <FormItem name="dbPort" label={res.database.dbPort}
                                                  rules={[{required: true}]}>
                                            <InputNumber min={1} max={65535} style={{width: "100%"}}
                                                         placeholder={`${getDefaultPort(currentDbType)}`}/>
                                        </FormItem>
                                    </div>
                                    <FormItem name="dbName" label={res.database.dbName}
                                              help={mysqlSelected ? res.database.dbNameHelp : undefined}
                                              rules={[{required: true}]}>
                                        <Input prefix={<DatabaseOutlined/>} placeholder="zrlog" autoComplete="off"/>
                                    </FormItem>
                                    <FormItem name="dbUserName" label={res.database.dbUserName}
                                              help={mysqlSelected ? res.database.dbUserHelp : undefined}
                                              rules={[{required: true}]}>
                                        <Input prefix={<UserOutlined/>} autoComplete="off"/>
                                    </FormItem>
                                    <FormItem name="dbPassword" label={res.database.dbPassword}>
                                        <Input.Password prefix={<LockOutlined/>} autoComplete="new-password"/>
                                    </FormItem>
                                    <Alert className="install-form-note" type="warning" showIcon
                                           message={res.database.initRisk}/>
                                </div>}
                                <div className="install-agreement-row">
                                    <Checkbox checked={state.agreementAccepted}
                                              aria-label={res.agreement.checkbox}
                                              onChange={(event) => setState((prevState) => ({
                                                  ...prevState,
                                                  agreementAccepted: event.target.checked,
                                              }))}>
                                        {res.agreement.checkbox}
                                    </Checkbox>
                                    <Button type="link" size="small" onClick={showDisclaimer}>
                                        {res.agreement.view}
                                    </Button>
                                </div>
                            </Form>
                        </>
                    )}
                    {state.current === 1 && (
                        <>
                            <div className="install-section-heading">
                                <span className="install-eyebrow">{formatText(res.wizard.stepCounter, {
                                    current: 2,
                                    total: steps.length,
                                })}</span>
                                <Title className="install-section-title" level={1} aria-live="polite">
                                    {res.website.title}
                                </Title>
                                <Paragraph className="install-section-description" type="secondary">
                                    {res.website.description}
                                </Paragraph>
                            </div>
                            {state.installError && (
                                <Alert
                                    type="error"
                                    showIcon
                                    message={getProgressText(state.installError)}
                                />
                            )}
                            <Form id="install-website-form" key="website-form" className="install-form"
                                  ref={formWeblogInfoRef} layout="vertical"
                                  scrollToFirstError={{focus: true}}
                                  initialValues={state.weblogInfo} disabled={state.installing}
                                  onFinish={() => void startInstall()}
                                  onValuesChange={(k: any, v: Record<string, string | number>) => setWeblogValue(k, v)}>
                                <FormItem name="username" label={res.website.admin}
                                          rules={[{required: true}, {max: 16}]}>
                                    <Input prefix={<UserOutlined/>} autoComplete="username"/>
                                </FormItem>
                                <FormItem name="password" label={res.website.adminPassword}
                                          rules={[{required: true}, {min: 8, message: res.website.passwordMin}]}>
                                    <Input.Password prefix={<LockOutlined/>} autoComplete="new-password"/>
                                </FormItem>
                                <FormItem name="confirmPassword" label={res.website.confirmPassword}
                                          dependencies={["password"]}
                                          rules={[{required: true}, ({getFieldValue}) => ({
                                              validator(_, value) {
                                                  return !value || getFieldValue("password") === value ? Promise.resolve() :
                                                      Promise.reject(new Error(res.website.passwordMismatch));
                                              },
                                          })]}>
                                    <Input.Password prefix={<LockOutlined/>} autoComplete="new-password"/>
                                </FormItem>
                                <FormItem name="email" label={res.website.adminEmail}
                                          rules={[{type: "email", message: res.website.emailInvalid}]}>
                                    <Input type="email" prefix={<GlobalOutlined/>} autoComplete="email"/>
                                </FormItem>
                                <FormItem name="title" label={res.website.siteTitle}
                                          rules={[{required: true}, {max: 255}]}>
                                    <Input prefix={<GlobalOutlined/>} placeholder={res.website.siteTitlePlaceholder}/>
                                </FormItem>
                                <FormItem name="second_title"
                                          label={res.website.siteSubtitle}>
                                    <Input/>
                                </FormItem>
                            </Form>
                            {renderProgress()}
                        </>
                    )}
                    {state.current === 2 && (
                        <InstallSuccessContent content={state.installSuccessContent}
                                               configurationRequired={state.configurationRequired}
                                               installToken={installToken}
                                               onInstallTokenChange={changeInstallToken}/>
                    )}
                        </div>
                {state.current <= 1 && <div className="install-actions">
                    {state.current === 0 && (
                        <Button className="install-action-button install-action-primary"
                                loading={state.testConnecting} disabled={nextDisabled} type="primary"
                                form="install-database-form" htmlType="submit">
                            {res.common.next}<ArrowRightOutlined aria-hidden="true"/>
                        </Button>
                    )}
                    {state.current === 1 && (
                        <>
                            <Button className="install-action-button" disabled={state.installing}
                                    icon={<ArrowLeftOutlined aria-hidden="true"/>}
                                    onClick={() => prev()}>
                                {res.common.previous}
                            </Button>
                            <Button className="install-action-button install-action-primary"
                                    loading={state.installing} disabled={state.installing} type="primary"
                                    form="install-website-form" htmlType="submit">
                                {state.installing ? res.website.installing : res.website.installAction}
                            </Button>
                        </>
                    )}
                </div>}
                    </main>
                </div>
                {showFeedback && <aside className="install-support" aria-label={res.feedback.title}>
                    <div className="install-support-group">
                        <QuestionCircleOutlined/>
                        <span>{res.feedback.title}</span>
                        <Button type="link" size="small" target="_blank" rel="noreferrer" href={res.feedbackUrl}>
                            {res.feedback.linkText}
                        </Button>
                    </div>
                    <UpgradeButton compact installToken={installToken}/>
                </aside>}
            </div>
            <footer className="install-footer">
                <span dangerouslySetInnerHTML={{__html: sanitizeRichHtml(res.copyrightTips)}}/>
                {res.copyrightSuffix}
            </footer>
        </Layout>
    );
}

export default IndexLayout;
