import {useEffect, useRef, useState} from 'react';
import {
    Alert,
    Button,
    Card,
    Checkbox,
    Collapse,
    Divider,
    Form,
    FormInstance,
    Grid,
    Input,
    Layout,
    List,
    message,
    Result,
    Segmented,
    Space,
    Steps,
    Tag,
    theme,
    Typography
} from 'antd';

import axios from "axios";
import Text from "antd/es/typography/Text";
import {formatText, getRes} from "../utils/constants";
import {mapToQueryString} from "../utils/helpers";
import DisclaimerAgreement from "./DisclaimerAgreement";
import UpgradeButton from './UpgradeButton';
import InstallSuccessContent from './InstallSuccessContent';
import EnvUtils, {ThemeMode} from "../utils/env-utils";

const FormItem = Form.Item;
const {Title, Paragraph} = Typography;
const {Footer} = Layout;
const {useBreakpoint} = Grid;

type ProbeStatus = "pass" | "warning" | "block";
type ProgressStatus = "running" | "complete" | "error";
type DbErrorCode = "DB_NOT_EXISTS" | "CREATE_CONNECT_ERROR" | "USERNAME_OR_PASSWORD_ERROR" | "SQL_EXCEPTION_UNKNOWN" | "MISSING_JDBC_DRIVER" | "UNSUPPORTED_DATABASE" | "UNKNOWN";

type InstallProbeItem = {
    code: string;
    category: string;
    status: ProbeStatus;
    value?: string;
    path?: string;
};

type InstallProbeData = {
    status: ProbeStatus;
    runtimeMode?: string;
    charset?: string;
    dbPropertiesPath?: string;
    lockFilePath?: string;
    items: InstallProbeItem[];
};

type InstallProgressEvent = {
    code: string;
    status: ProgressStatus;
    detail?: string;
};

type ApiError = {
    code?: string;
    message?: string;
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
    dbError?: ApiError;
    installError?: InstallProgressEvent;
    progressEvents: InstallProgressEvent[];
    dataBaseInfo: Record<string, string | number>;
    weblogInfo: Record<string, string | number>;
    installSuccessContent: string;
    askConfig: boolean;
}

const formItemLayout = {
    labelCol: {
        xs: {span: 24},
        sm: {span: 6},
        md: {span: 5},
    },
    wrapperCol: {
        xs: {span: 24},
        sm: {span: 14},
        md: {span: 11},
    },
};

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
    const lines = chunk.split("\n");
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

const parseDbErrorCode = (messageText?: string): DbErrorCode | undefined => {
    if (!messageText) {
        return undefined;
    }
    const matched = messageText.match(/\[Error-([A-Z_]+)]/);
    if (!matched) {
        return undefined;
    }
    return matched[1] as DbErrorCode;
};

const getDbErrorText = (error?: ApiError) => {
    if (!error) {
        return "";
    }
    const res = getRes();
    const code = (error.code || parseDbErrorCode(error.message)) as DbErrorCode | undefined;
    if (code && res.error.db[code]) {
        return res.error.db[code];
    }
    return error.message || res.error.unknown;
};

const getProbeItemDescription = (item: InstallProbeItem) => {
    const res = getRes();
    const itemText = res.probe.item[item.code as keyof typeof res.probe.item];
    const template = itemText?.[item.status] || item.value || item.code;
    const pathText = item.path ? ` ${item.path}` : "";
    return `${formatText(template, {value: item.value})}${pathText}`;
};

const getProgressText = (event: InstallProgressEvent) => {
    const res = getRes();
    const progressText = res.progress.item[event.code as keyof typeof res.progress.item] || event.code;
    if (event.status === "error" && event.detail) {
        return `${progressText}: ${event.detail}`;
    }
    return progressText;
};

const progressStepCodes = ["preflight", "database", "schema", "seed-website", "seed-admin", "seed-defaults", "config"];

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
    const [state, setState] = useState<AppState>({
        current: 0,
        installed: res.installed === true,
        askConfig: res.askConfig === true,
        testConnecting: false,
        agreementAccepted: false,
        probeLoading: false,
        installSuccessContent: res.installSuccessContent || "",
        installing: false,
        progressEvents: [],
        dataBaseInfo: {
            dbType: "mysql",
            dbPort: getDefaultPort("mysql"),
        },
        weblogInfo: {},
    });

    const formDataBaseInfoRef = useRef<FormInstance>(null);
    const formWeblogInfoRef = useRef<FormInstance>(null);
    const [messageApi, contextHolder] = message.useMessage({maxCount: 3});
    const screens = useBreakpoint();
    const {token} = theme.useToken();
    const [themeMode, setThemeMode] = useState<ThemeMode>(EnvUtils.getThemeMode());

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

    const loadProbe = () => {
        setState((prevState) => ({...prevState, probeLoading: true, probeError: undefined}));
        axios.get("/api/install/probe").then(({data}) => {
            setState((prevState) => ({
                ...prevState,
                probe: data.data,
                probeLoading: false,
                probeError: undefined,
            }));
        }).catch((error) => {
            setState((prevState) => ({
                ...prevState,
                probeLoading: false,
                probeError: error?.message || res.error.requestError,
            }));
        });
    };

    useEffect(() => {
        if (!state.installed) {
            loadProbe();
        }
    }, []);

    const getSteps = () => {
        return [
            {
                title: res.wizard.databaseStep,
            }, {
                title: res.wizard.websiteStep,
            }, {
                title: res.wizard.completeStep,
            }
        ]
    }

    const setDatabaseValue = (changedValues: Record<string, string | number>, allValues: Record<string, string | number>) => {
        if (!formDataBaseInfoRef.current) {
            return;
        }
        const nextValues = {...allValues};
        if (changedValues.dbType !== undefined) {
            const newPort = getDefaultPort(changedValues.dbType as string);
            nextValues.dbPort = newPort;
            formDataBaseInfoRef.current.setFieldValue("dbPort", newPort)
        }
        formDataBaseInfoRef.current.setFieldsValue(changedValues);
        setState((prevState) => ({
            ...prevState,
            dataBaseInfo: nextValues,
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
        if (!state.agreementAccepted || state.probe?.status === "block") {
            return;
        }
        const dataBaseInfo = await formDataBaseInfoRef.current?.validateFields();
        setState((prevState) => ({...prevState, testConnecting: true, dbError: undefined}));
        axios.get("/api/install/testDbConn?" + mapToQueryString(dataBaseInfo || state.dataBaseInfo)).then(({data}) => {
            if (!data.error) {
                setState((prevState) => ({...prevState, current: 1, testConnecting: false, dataBaseInfo: dataBaseInfo || prevState.dataBaseInfo}));
                return;
            }
            setState((prevState) => ({
                ...prevState,
                dbError: {code: data.code, message: data.message},
                testConnecting: false,
            }));
        }).catch((error) => {
            setState((prevState) => ({
                ...prevState,
                dbError: {
                    code: error?.response?.data?.code,
                    message: error?.response?.data?.message || error?.message || res.error.requestError,
                },
                testConnecting: false,
            }));
        });
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
            setState((prevState) => ({
                ...prevState,
                installing: false,
                installError: event.data,
                progressEvents: mergeProgressEvent(prevState.progressEvents, event.data),
            }));
            return;
        }
        if (event.event === "install-complete") {
            setState((prevState) => ({
                ...prevState,
                current: 2,
                installing: false,
                installSuccessContent: event.data?.data?.content || "",
            }));
        }
    };

    const startInstall = async () => {
        const weblogInfo = await formWeblogInfoRef.current?.validateFields();
        setState((prevState) => ({
            ...prevState,
            installing: true,
            installError: undefined,
            progressEvents: [],
            weblogInfo: weblogInfo || prevState.weblogInfo,
        }));
        let response: Response;
        const installQuery = mapToQueryString({
            ...state.dataBaseInfo,
            ...(weblogInfo || state.weblogInfo)
        });
        try {
            response = await fetch("/api/install/startInstall?" + installQuery, {
                headers: {
                    Accept: "text/event-stream",
                },
            });
        } catch (error: any) {
            messageApi.error(error?.message || res.error.requestError);
            setState((prevState) => ({...prevState, installing: false}));
            return;
        }
        if (!response.ok) {
            messageApi.error(`${res.error.requestError}: ${response.status}`);
            setState((prevState) => ({...prevState, installing: false}));
            return;
        }
        const contentType = response.headers.get("content-type");
        if (!contentType || !contentType.includes("text/event-stream")) {
            const data = await response.json();
            if (!data.error) {
                setState((prevState) => ({
                    ...prevState,
                    current: 2,
                    installing: false,
                    installSuccessContent: data.data?.content || "",
                }));
                return;
            }
            setState((prevState) => ({
                ...prevState,
                installing: false,
                installError: {code: "install", status: "error", detail: data.message},
            }));
            return;
        }
        const reader = response.body?.getReader();
        if (!reader) {
            messageApi.error(res.error.requestError);
            setState((prevState) => ({...prevState, installing: false}));
            return;
        }
        const decoder = new TextDecoder();
        let buffer = "";
        for (;;) {
            const {done, value} = await reader.read();
            if (done) {
                buffer += decoder.decode();
            } else {
                buffer += decoder.decode(value, {stream: true});
            }
            const chunks = buffer.split("\n\n");
            buffer = done ? "" : chunks.pop() || "";
            for (const chunk of chunks) {
                const event = parseSseEvent(chunk);
                if (event) {
                    handleInstallSseEvent(event);
                }
            }
            if (done) {
                break;
            }
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
            return <Space wrap size={[8, 4]} align="center">
                <Tag color="success" style={{marginInlineEnd: 0}}>{messageText}</Tag>
                <Text type="secondary">{res.probe.title}</Text>
                <Button type="link" size="small" onClick={loadProbe}>{res.probe.retry}</Button>
            </Space>;
        }
        return <Space direction="vertical" style={{width: "100%"}}>
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
                        children: <List
                            size="small"
                            dataSource={state.probe.items}
                            renderItem={(item) => (
                                <List.Item>
                                    <Space direction="vertical" size={2}>
                                        <Space>
                                            <Tag color={item.status === "pass" ? "green" : item.status === "warning" ? "gold" : "red"}>
                                                {res.probe[item.status]}
                                            </Tag>
                                            <Text strong>{res.probe.item[item.code as keyof typeof res.probe.item]?.title || item.code}</Text>
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
        return <div style={{
            marginTop: 8,
            padding: "10px 12px",
            border: `1px solid ${token.colorBorderSecondary}`,
            borderRadius: token.borderRadius,
            background: token.colorFillQuaternary,
        }}>
            <Space direction="vertical" size={6} style={{width: "100%"}}>
                <Space wrap size={[8, 4]}>
                    <Text strong>{res.progress.title}</Text>
                    {currentEvent && <Tag color={getProgressColor(currentEvent.status)} style={{marginInlineEnd: 0}}>
                        {getProgressStatusText(currentEvent.status)}
                    </Tag>}
                    {currentEvent && <Text type={currentEvent.status === "error" ? "danger" : "secondary"}>
                        {getProgressText(currentEvent)}
                    </Text>}
                    {!state.installError && <Text type="secondary">{completedCount}/{progressStepCodes.length}</Text>}
                </Space>
                {state.progressEvents.length > 0 && (
                    <Space wrap size={[4, 4]}>
                        {state.progressEvents.map((event) => (
                            <Tag key={event.code} color={getProgressColor(event.status)} style={{marginInlineEnd: 0}}>
                                {getProgressText(event)}
                            </Tag>
                        ))}
                    </Space>
                )}
            </Space>
        </div>
    };

    if (state.installed) {
        if (state.askConfig) {
            return <Layout style={{
                minHeight: "100vh", paddingRight: 12,
                paddingLeft: 12, display: "flex", alignItems: "center"
            }}>
                <InstallSuccessContent content={state.installSuccessContent} askConfig={state.askConfig}/>
            </Layout>
        }
        return <Result status={"error"} title={res.installedPage.title} subTitle={res.warMode ? res.installedPage.warTips : res.installedPage.tips}/>
    }

    const showFeedback = state.current <= 1;
    const nextDisabled = !state.agreementAccepted || state.probeLoading || state.probe?.status === "block";
    const currentDbType = state.dataBaseInfo.dbType as string;
    const mysqlSelected = currentDbType === "mysql";
    const sqliteSelected = currentDbType === "sqlite";
    const localSqliteAvailable = res.localSqliteAvailable === true;
    const compactView = !screens.sm;
    const cardTitle = (
        <div style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            gap: 12,
            flexWrap: "wrap",
            width: "100%",
        }}>
            <span>{res.wizard.title}</span>
            <Segmented
                size="small"
                value={themeMode}
                onChange={(value) => changeThemeMode(value as ThemeMode)}
                options={[
                    {label: res.theme.light, value: "light"},
                    {label: res.theme.dark, value: "dark"},
                    {label: res.theme.system, value: "system"},
                ]}
            />
        </div>
    );

    return (
        <Layout style={{
            minHeight: "100vh", paddingRight: 12,
            paddingLeft: 12, display: "flex", alignItems: "center"
        }}>
            {contextHolder}
            <Card title={cardTitle} style={{
                marginTop: compactView ? 12 : 32, marginBottom: compactView ? 16 : 32, width: "100%",
                maxWidth: 960
            }} styles={{body: {padding: compactView ? 16 : undefined}}}>
                <Steps current={state.current} items={getSteps()} size={compactView ? "small" : "default"}/>
                <div style={{marginTop: compactView ? 16 : 20}}>
                    {state.current === 0 && (
                        <Space direction="vertical" size={16} style={{width: "100%"}}>
                            {renderProbe()}
                            <div>
                                <Title level={3}>{res.database.title}</Title>
                                <Paragraph type="secondary">{res.database.description}</Paragraph>
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
                            <Form ref={formDataBaseInfoRef} initialValues={state.dataBaseInfo} {...formItemLayout}
                                  onValuesChange={(k: any, v: any) => setDatabaseValue(k, v)}>
                                <FormItem name='dbType' label={res.database.dbType}
                                          rules={[{required: true}]}>
                                    <Segmented block options={[
                                        {label: compactView ? "MySQL" : "MySQL / MariaDB", value: "mysql"},
                                        ...(localSqliteAvailable ? [{label: "SQLite", value: "sqlite"}] : []),
                                        {label: "WebApi", value: "webapi"},
                                    ]}/>
                                </FormItem>
                                {!sqliteSelected && <>
                                    <FormItem name='dbHost' label={res.database.dbHost}
                                              rules={[{required: true}]}>
                                        <Input placeholder='127.0.0.1'/>
                                    </FormItem>
                                    <FormItem name='dbName' label={res.database.dbName}
                                              help={mysqlSelected ? res.database.dbNameHelp : undefined}
                                              rules={[{required: true}]}>
                                        <Input placeholder='ZrLog'/>
                                    </FormItem>
                                    <FormItem name='dbUserName' label={res.database.dbUserName}
                                              help={mysqlSelected ? res.database.dbUserHelp : undefined}
                                              rules={[{required: true}]}>
                                        <Input placeholder=''/>
                                    </FormItem>
                                    <FormItem name='dbPassword' label={res.database.dbPassword}>
                                        <Input type='password'/>
                                    </FormItem>
                                    <FormItem name='dbPort' label={res.database.dbPort}
                                              rules={[{required: true}]}>
                                        <Input type='number'
                                               placeholder={getDefaultPort(currentDbType) + ""}
                                               style={{maxWidth: 108}}/>
                                    </FormItem>
                                </>}
                            </Form>
                            <Alert type="info" showIcon message={res.database.initRisk}/>
                            <Checkbox checked={state.agreementAccepted}
                                      onChange={(event) => setState((prevState) => ({...prevState, agreementAccepted: event.target.checked}))}>
                                {res.agreement.checkbox}
                            </Checkbox>
                            <Collapse size="small" items={[{
                                key: "agreement",
                                label: res.agreement.title,
                                children: <DisclaimerAgreement/>,
                            }]}/>
                        </Space>
                    )}
                    {state.current === 1 && (
                        <Space direction="vertical" size={16} style={{width: "100%"}}>
                            <div>
                                <Title level={3}>{res.website.title}</Title>
                                <Paragraph type="secondary">{res.website.description}</Paragraph>
                            </div>
                            {state.installError && (
                                <Alert
                                    type="error"
                                    showIcon
                                    message={getProgressText(state.installError)}
                                />
                            )}
                            <Form ref={formWeblogInfoRef} {...formItemLayout}
                                  onValuesChange={(k: any, v: Record<string, string | number>) => setWeblogValue(k, v)}>
                                <FormItem name='username' label={res.website.admin}
                                          rules={[{required: true}]}>
                                    <Input placeholder='admin'/>
                                </FormItem>
                                <FormItem name='password' label={res.website.adminPassword}
                                          rules={[{required: true}]}>
                                    <Input type='password'/>
                                </FormItem>
                                <FormItem name='email' label={res.website.adminEmail}>
                                    <Input type='email'/>
                                </FormItem>
                                <FormItem name='title' label={res.website.siteTitle}
                                          rules={[{required: true}]}>
                                    <Input placeholder={res.website.siteTitlePlaceholder}/>
                                </FormItem>
                                <FormItem name='second_title'
                                          label={res.website.siteSubtitle}>
                                    <Input/>
                                </FormItem>
                            </Form>
                            {renderProgress()}
                        </Space>
                    )}
                    {state.current === 2 && (
                        <div style={{textAlign: "center"}}>
                            <InstallSuccessContent content={state.installSuccessContent} askConfig={state.askConfig}/>
                        </div>
                    )}
                </div>
                <div style={{paddingTop: state.current <= 1 ? 20 : 0}}>
                    {state.current === 0 && (
                        <Button loading={state.testConnecting} disabled={nextDisabled} type="primary" onClick={() => nextFromDatabase()}>
                            {res.common.next}
                        </Button>
                    )}
                    {state.current === 1 && (
                        <>
                            <Button loading={state.installing} type="primary" onClick={() => startInstall()}>
                                {state.installing ? res.website.installing : res.website.installAction}
                            </Button>
                            <Button disabled={state.installing} style={{margin: '0 8px'}} onClick={() => prev()}>
                                {res.common.previous}
                            </Button>
                        </>
                    )}
                </div>
                {showFeedback && (
                    <>
                        <Divider/>
                        <Space direction="vertical" size={12} style={{width: "100%"}}>
                            <UpgradeButton/>
                            <Alert
                                type="info"
                                showIcon
                                message={res.feedback.title}
                                description={<span>{res.feedback.content} <a target="_blank" rel="noreferrer" href={res.feedbackUrl}>{res.feedback.linkText}</a></span>}
                            />
                        </Space>
                    </>
                )}
            </Card>
            <Footer style={{textAlign: 'center'}}>
                <span dangerouslySetInnerHTML={{__html: res.copyrightTips}}/>. All Rights Reserved.
            </Footer>
        </Layout>
    );
}

export default IndexLayout;
