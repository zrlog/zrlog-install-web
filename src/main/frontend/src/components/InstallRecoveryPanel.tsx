import {ArrowRightOutlined, HistoryOutlined} from "@ant-design/icons";
import {Alert, App, Button, Form, Input, Tag, Typography} from "antd";
import type {InputRef} from "antd";
import {useEffect, useRef, useState} from "react";
import {getRes} from "utils/constants";
import {
    installTokenForRequest,
    isRequiredInstallTokenMissing,
    readInstallResultContent,
    requestResumeInstall,
} from "utils/install-api";

const {Paragraph, Text, Title} = Typography;

type InstallRecoveryPanelProps = {
    installToken: string;
    onRecovered: (content: string) => void;
    onInstallTokenChange: (installToken: string) => void;
    onRuntimeStateChanged: () => void;
    onStartNew: () => void;
};

const InstallRecoveryPanel = ({
    installToken,
    onRecovered,
    onInstallTokenChange,
    onRuntimeStateChanged,
    onStartNew,
}: InstallRecoveryPanelProps) => {
    const res = getRes();
    const {modal} = App.useApp();
    const [resuming, setResuming] = useState(false);
    const [tokenTouched, setTokenTouched] = useState(false);
    const [resumeFailed, setResumeFailed] = useState(false);
    const installTokenInputRef = useRef<InputRef>(null);
    const installTokenRequired = res.installTokenRequired;
    const tokenInvalid = tokenTouched && isRequiredInstallTokenMissing(installTokenRequired, installToken);

    useEffect(() => {
        if (tokenInvalid) {
            installTokenInputRef.current?.focus();
        }
    }, [tokenInvalid]);

    const changeInstallToken = (nextInstallToken: string) => {
        onInstallTokenChange(nextInstallToken);
        setResumeFailed(false);
        if (nextInstallToken.trim()) {
            setTokenTouched(false);
        }
    };

    const resumeInstall = async () => {
        if (resuming) {
            return;
        }
        if (isRequiredInstallTokenMissing(installTokenRequired, installToken)) {
            setTokenTouched(true);
            return;
        }
        setResuming(true);
        setResumeFailed(false);
        try {
            const response = await requestResumeInstall(
                installTokenForRequest(installTokenRequired, installToken),
            );
            if (!response.ok) {
                throw new Error();
            }
            const content = readInstallResultContent(await response.json());
            if (content === undefined) {
                throw new Error();
            }
            onRecovered(content);
        } catch {
            setResumeFailed(true);
            setResuming(false);
            onRuntimeStateChanged();
        }
    };

    const confirmStartNew = () => {
        modal.confirm({
            title: res.recovery.startNewConfirmTitle,
            content: res.recovery.startNewConfirmContent,
            okText: res.recovery.startNew,
            cancelText: res.common.cancel,
            onOk: onStartNew,
        });
    };

    return <section className="install-recovery" aria-labelledby="install-recovery-title">
        <Tag className="install-recovery-status" icon={<HistoryOutlined/>} color="processing">
            {res.recovery.status}
        </Tag>
        <Title className="install-recovery-title" id="install-recovery-title" level={1} aria-live="polite">
            {res.recovery.title}
        </Title>
        <Paragraph type="secondary" className="install-recovery-description">
            {res.recovery.description}
        </Paragraph>
        <Alert className="install-recovery-notice"
               type="info"
               showIcon
               message={res.recovery.noticeTitle}
               description={res.recovery.noticeDescription}/>
        {resumeFailed && <Alert className="install-recovery-error"
                                type="error"
                                showIcon
                                role="alert"
                                message={res.recovery.failedTitle}
                                description={res.recovery.failedDescription}/>
        }
        <Form className="install-recovery-form" layout="vertical" onFinish={() => void resumeInstall()}>
            {installTokenRequired && <Form.Item label={res.security.tokenLabel}
                       htmlFor="install-recovery-token"
                       required
                       validateStatus={tokenInvalid ? "error" : undefined}
                       help={tokenInvalid ? <span id="install-recovery-token-error" role="alert">
                           {res.security.tokenRequired}
                       </span> : undefined}
                       extra={<Text id="install-recovery-token-help" type="secondary">
                           {res.security.tokenHelp}
                       </Text>}>
                <Input.Password id="install-recovery-token"
                                ref={installTokenInputRef}
                                value={installToken}
                                onChange={(event) => changeInstallToken(event.target.value)}
                                placeholder={res.security.tokenPlaceholder}
                                autoComplete="off"
                                aria-describedby={`install-recovery-token-help${tokenInvalid ? " install-recovery-token-error" : ""}`}
                                aria-errormessage={tokenInvalid ? "install-recovery-token-error" : undefined}
                                aria-required="true"
                                aria-invalid={tokenInvalid}/>
            </Form.Item>}
            <div className="install-recovery-actions">
                <Button className="install-recovery-action" type="primary" size="large"
                        htmlType="submit" loading={resuming}
                        icon={<ArrowRightOutlined aria-hidden="true"/>}>
                    {resuming ? res.recovery.resuming : res.recovery.resume}
                </Button>
                <Button className="install-recovery-action" size="large" htmlType="button"
                        disabled={resuming} onClick={confirmStartNew}>
                    {res.recovery.startNew}
                </Button>
            </div>
        </Form>
    </section>;
};

export default InstallRecoveryPanel;
