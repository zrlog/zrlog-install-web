import {CheckCircleFilled, CopyOutlined, EditOutlined, FileMarkdownOutlined} from "@ant-design/icons";
import {Button, Input, message, Space, Typography} from "antd";
import type {InputRef} from "antd";
import axios from "axios";
import {useEffect, useRef, useState} from "react";
import {getRes} from "utils/constants";
import {renderSanitizedMarkdown} from "utils/sanitize-html";
import {buildInstallHandoffUrls, extractDbProperties} from "utils/install-completion";
import {
    installApiUrl,
    installTokenForRequest,
    isRequiredInstallTokenMissing,
    requestInstallCompletion,
} from "utils/install-api";

const {Paragraph, Text, Title} = Typography;

const InstallSuccessContent = ({content, configurationRequired, installToken, onInstallTokenChange}: {
    content: string,
    configurationRequired: boolean,
    installToken: string,
    onInstallTokenChange: (installToken: string) => void,
}) => {
    const res = getRes();
    const [messageApi, contextHolder] = message.useMessage({maxCount: 3});
    const [checkingConfig, setCheckingConfig] = useState(false);
    const [loadingCompletion, setLoadingCompletion] = useState(false);
    const [completionContent, setCompletionContent] = useState(content);
    const [completionTokenTouched, setCompletionTokenTouched] = useState(false);
    const completionTokenInputRef = useRef<InputRef>(null);
    const safeContent = renderSanitizedMarkdown(completionContent);
    const dbProperties = extractDbProperties(completionContent);
    const handoffUrls = buildInstallHandoffUrls(document.baseURI);
    const installTokenRequired = res.installTokenRequired;
    const completionTokenInvalid = completionTokenTouched &&
        isRequiredInstallTokenMissing(installTokenRequired, installToken);

    useEffect(() => setCompletionContent(content), [content]);

    useEffect(() => {
        if (completionTokenInvalid) {
            completionTokenInputRef.current?.focus();
        }
    }, [completionTokenInvalid]);

    const changeCompletionToken = (nextInstallToken: string) => {
        onInstallTokenChange(nextInstallToken);
        if (nextInstallToken.trim()) {
            setCompletionTokenTouched(false);
        }
    };

    const loadCompletion = async () => {
        if (isRequiredInstallTokenMissing(installTokenRequired, installToken)) {
            setCompletionTokenTouched(true);
            return;
        }
        setLoadingCompletion(true);
        try {
            const response = await requestInstallCompletion(
                installTokenForRequest(installTokenRequired, installToken),
            );
            if (!response.ok) {
                throw new Error(res.installedPage.completionUnavailable);
            }
            const responseBody = await response.json();
            const nextContent = responseBody?.data?.content;
            if (typeof nextContent !== "string" || !nextContent.trim()) {
                throw new Error(res.installedPage.completionUnavailable);
            }
            setCompletionContent(nextContent);
        } catch {
            messageApi.error(res.installedPage.completionUnavailable);
        } finally {
            setLoadingCompletion(false);
        }
    };

    const checkConfig = async () => {
        setCheckingConfig(true);
        try {
            const {data} = await axios.get(installApiUrl("installResource"));
            if (data?.error !== 0 || data?.data?.installed !== true) {
                throw new Error(res.error.requestError);
            }
            if (data.data.missingConfig === true) {
                messageApi.error(res.installedPage.missingConfigTips);
                return;
            }
            window.location.href = document.baseURI;
        } catch {
            messageApi.error(res.error.requestError);
        } finally {
            setCheckingConfig(false);
        }
    };

    const copyDbProperties = async () => {
        if (dbProperties === undefined) {
            return;
        }
        try {
            let copied = false;
            if (navigator.clipboard?.writeText) {
                try {
                    await navigator.clipboard.writeText(dbProperties);
                    copied = true;
                } catch {
                    // Some HTTP and embedded browsers expose Clipboard API but reject writes.
                }
            }
            if (!copied) {
                const previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null;
                const textArea = document.createElement("textarea");
                textArea.value = dbProperties;
                textArea.setAttribute("readonly", "");
                textArea.style.position = "fixed";
                textArea.style.opacity = "0";
                document.body.appendChild(textArea);
                try {
                    textArea.select();
                    copied = document.execCommand("copy");
                } finally {
                    textArea.remove();
                    previouslyFocused?.focus();
                }
                if (!copied) {
                    throw new Error("copy failed");
                }
            }
            messageApi.success(res.success.dbPropertiesCopied);
        } catch {
            messageApi.error(res.success.dbPropertiesCopyFailed);
        }
    };

    return <section className="install-success" aria-labelledby="install-success-title">
        {contextHolder}
        <div className="install-success-mark" aria-hidden="true"><CheckCircleFilled/></div>
        <Title className="install-success-title" id="install-success-title" level={1} aria-live="polite">
            {configurationRequired ? res.success.askConfigTitle : res.success.installSuccess}
        </Title>
        <Paragraph type="secondary">
            {configurationRequired ? res.success.askConfigDescription : res.success.installDescription}
        </Paragraph>
        {configurationRequired && !safeContent && <form className="install-completion-recovery"
                                            onSubmit={(event) => {
                                                event.preventDefault();
                                                void loadCompletion();
            }}>
            <Paragraph className="install-completion-description" type="secondary">
                {res.installedPage.completionRecovery}
            </Paragraph>
            {installTokenRequired && <>
                <label htmlFor="install-completion-token"><Text strong>{res.security.tokenLabel}</Text></label>
                <Input.Password id="install-completion-token"
                            ref={completionTokenInputRef}
                            value={installToken}
                            onChange={(event) => changeCompletionToken(event.target.value)}
                            placeholder={res.security.tokenPlaceholder}
                            autoComplete="off"
                            aria-describedby={`install-completion-token-help${completionTokenInvalid ? " install-completion-token-error" : ""}`}
                            aria-errormessage={completionTokenInvalid ? "install-completion-token-error" : undefined}
                            aria-invalid={completionTokenInvalid}
                            aria-required="true"/>
                {completionTokenInvalid && <Text id="install-completion-token-error" type="danger" role="alert">
                    {res.security.tokenRequired}
                </Text>}
                <Text id="install-completion-token-help" type="secondary" className="install-token-help">
                    {res.security.tokenHelp}
                </Text>
            </>}
            <Button className="install-completion-action" size="large" type="primary"
                    htmlType="submit" loading={loadingCompletion}>
                {loadingCompletion ? res.installedPage.loadingCompletion : res.installedPage.loadCompletion}
            </Button>
        </form>}
        {safeContent && <div className="install-success-content" role="region" tabIndex={0}
                             aria-label={res.success.configurationInstructions}>
            {dbProperties !== undefined && <div className="install-success-content-toolbar">
                <Text code>DB_PROPERTIES</Text>
                <Button icon={<CopyOutlined aria-hidden="true"/>} onClick={() => void copyDbProperties()}>
                    {res.success.copyDbProperties}
                </Button>
            </div>}
            <Typography className="install-success-markdown"
                        dangerouslySetInnerHTML={{__html: safeContent}}/>
        </div>}
        {(!configurationRequired || Boolean(safeContent)) && <Space className="install-success-actions" size={12} wrap>
            {configurationRequired ?
                <Button size="large" type="primary" loading={checkingConfig} onClick={() => void checkConfig()}>
                    {checkingConfig ? res.installedPage.checkingConfig : res.installedPage.askConfigTips}
                </Button> : <>
                    <Button href={handoffUrls.admin} size="large" type="primary">{res.success.enterAdmin}</Button>
                    <Button href={handoffUrls.createArticle} icon={<EditOutlined/>} size="large">
                        {res.success.createArticle}
                    </Button>
                    <Button href={handoffUrls.importMarkdown} icon={<FileMarkdownOutlined/>} size="large">
                        {res.success.importMarkdown}
                    </Button>
                    <Button href={document.baseURI} size="large">{res.success.viewSite}</Button>
                </>}
        </Space>}
    </section>;
};

export default InstallSuccessContent;
