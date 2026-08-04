export type InstallLang = "en_US" | "zh_CN";

const zhCN = {
    common: {
        confirm: "确认",
        retry: "重试",
        detail: "详情",
        download: "下载",
        loading: "加载中",
        previous: "上一步",
        next: "下一步",
        complete: "完成",
        view: "查看",
        close: "关闭",
    },
    wizard: {
        title: "ZrLog 安装向导",
        databaseStep: "数据库配置",
        websiteStep: "站点信息",
        completeStep: "完成",
    },
    theme: {
        light: "浅色",
        dark: "深色",
        system: "系统",
    },
    upgrade: {
        newVersion: "有新版本",
        newVersionTip: "发现新版本 v{version}",
        action: "立即升级",
        upgrading: "正在升级",
        confirmTitle: "升级到最新版本",
        confirmContent: "ZrLog 将下载并校验官方更新包，完成后自动重启。",
        complete: "升级已提交，正在等待重启",
        failed: "升级失败",
    },
    feedback: {
        title: "安装遇到问题？",
        content: "先检查数据库连接和目录权限，仍有问题可反馈",
        linkText: "提交反馈",
    },
    installedPage: {
        title: "已安装",
        tips: "如需重新安装，请删除 conf/install.lock 文件",
        warTips: "如需重新安装，请删除 WEB-INF/install.lock 文件",
        askConfigTips: "已完成配置？",
        missingConfigTips: "保存完成页配置后，再重启或重新部署",
    },
    agreement: {
        checkbox: "我已阅读并同意免责协议",
        title: "免责协议",
        content: `<h3 style="text-align:center">ZrLog 博客程序免责协议</h3>

#### 开源协议

ZrLog 是开源项目，遵循 Apache 2.0 许可证。继续安装表示您接受该许可证。

#### 数据安全

请自行确认部署环境，并定期备份数据。网络、服务器、兼容性等问题都可能影响访问或造成数据丢失。

#### 内容责任

请遵守法律法规。站点内容由使用者自行负责。

#### 免责说明

因安装、使用或无法使用 ZrLog 造成的损失，开发团队不承担赔偿责任，法律另有规定除外。`,
    },
    database: {
        title: "连接数据库",
        description: "填好连接信息，先试连，再配置站点",
        dbType: "数据库类型",
        dbHost: "主机地址",
        dbName: "数据库名",
        dbUserName: "用户名",
        dbPassword: "密码",
        dbPort: "端口",
        dbNameHelp: "数据库不存在时会尝试自动创建",
        dbUserHelp: "远程连接不建议使用 root",
        initRisk: "会写入 ZrLog 数据表和默认内容，建议使用空库",
        testFailedTitle: "数据库连接失败",
        testFailedAction: "检查地址、端口、账号、密码和授权后重试",
    },
    website: {
        title: "设置站点和管理员",
        description: "先创建管理员和站点名，之后可在后台修改",
        admin: "管理员账号",
        adminPassword: "管理员密码",
        adminEmail: "管理员邮箱",
        siteTitle: "站点标题",
        siteTitlePlaceholder: "站点名称",
        siteSubtitle: "站点副标题",
        installAction: "开始安装",
        installing: "正在安装",
        installIntro: "开始后会显示进度",
    },
    probe: {
        title: "安装环境",
        checking: "正在检查环境",
        pass: "环境检查通过",
        warning: "环境有提醒",
        block: "先处理环境问题",
        retry: "重新检查",
        expand: "查看详情",
        item: {
            "runtime.mode": {
                title: "运行模式",
                pass: "运行模式：{value}",
                warning: "运行模式：{value}",
                block: "未识别运行模式",
            },
            "runtime.charset": {
                title: "系统编码",
                pass: "系统编码：{value}",
                warning: "不是 UTF-8，中文可能乱码",
                block: "系统编码异常",
            },
            "file.dbProperties": {
                title: "数据库配置文件",
                pass: "数据库配置文件可写",
                warning: "安装时会创建数据库配置文件",
                block: "数据库配置文件不可写，请检查目录权限",
            },
            "file.installLock": {
                title: "安装锁文件",
                pass: "安装锁文件可写",
                warning: "安装时会创建安装锁文件",
                block: "安装锁文件不可写，请检查目录权限",
            },
            "runtime.dockerMount": {
                title: "Docker 持久化",
                pass: "Docker 配置目录可用",
                warning: "确认已挂载配置目录，例如 /opt/zrlog/conf",
                block: "Docker 配置目录不可写，请检查挂载和权限",
            },
            "runtime.faasAskConfig": {
                title: "Serverless 配置交付",
                pass: "Serverless 配置检查通过",
                warning: "安装完成后，按完成页保存配置",
                block: "Serverless 配置不可用",
            },
        },
    },
    progress: {
        title: "安装进度",
        running: "进行中",
        complete: "完成",
        error: "失败",
        item: {
            preflight: "检查环境",
            database: "连接数据库",
            schema: "初始化表结构",
            "seed-website": "写入站点信息",
            "seed-admin": "创建管理员",
            "seed-defaults": "写入默认内容",
            config: "保存配置",
            install: "安装",
        },
    },
    error: {
        requestError: "请求失败",
        unknown: "未知错误",
        db: {
            DB_NOT_EXISTS: "数据库不存在，自动创建也失败了，请手动创建后重试",
            CREATE_CONNECT_ERROR: "连不上数据库，请检查地址、端口、网络和监听地址",
            USERNAME_OR_PASSWORD_ERROR: "账号或密码不对，或没有远程访问权限",
            SQL_EXCEPTION_UNKNOWN: "数据库返回异常，请查看数据库或应用日志",
            MISSING_JDBC_DRIVER: "缺少 JDBC 驱动，安装包可能不完整",
            UNKNOWN: "数据库检查失败，请查看日志后重试",
        },
    },
    success: {
        installSuccess: "安装完成",
        viewSite: "查看站点",
        askConfigTitle: "后续配置",
    },
    copyright: "Copyright © 2013-2026",
};

const enUS: typeof zhCN = {
    common: {
        confirm: "OK",
        retry: "Retry",
        detail: "Details",
        download: "Download",
        loading: "Loading",
        previous: "Previous",
        next: "Next",
        complete: "Complete",
        view: "View",
        close: "Close",
    },
    wizard: {
        title: "ZrLog Setup",
        databaseStep: "Database",
        websiteStep: "Site Info",
        completeStep: "Complete",
    },
    theme: {
        light: "Light",
        dark: "Dark",
        system: "System",
    },
    upgrade: {
        newVersion: "New version",
        newVersionTip: "New version v{version} is available",
        action: "Upgrade now",
        upgrading: "Upgrading",
        confirmTitle: "Upgrade to the latest version",
        confirmContent: "ZrLog will download and verify the official package, then restart automatically.",
        complete: "Upgrade submitted; waiting for restart",
        failed: "Upgrade failed",
    },
    feedback: {
        title: "Installation problem?",
        content: "Check database access and directory permissions first. If it still fails, send feedback.",
        linkText: "Submit feedback",
    },
    installedPage: {
        title: "Installed",
        tips: "Delete conf/install.lock if you need to reinstall.",
        warTips: "Delete WEB-INF/install.lock if you need to reinstall.",
        askConfigTips: "Configuration completed?",
        missingConfigTips: "Save the configuration from the completion page, then restart or redeploy.",
    },
    agreement: {
        checkbox: "I have read and agree to the disclaimer",
        title: "Disclaimer",
        content: `<h3 style="text-align:center">ZrLog Blog Software Disclaimer Agreement</h3>

#### Open Source License

ZrLog is open source and licensed under Apache 2.0. Continuing setup means you accept that license.

#### Data Safety

Please check your deployment environment and keep regular backups. Network, server, or compatibility issues may interrupt access or cause data loss.

#### Content Responsibility

Please comply with applicable laws. Site content is the user's responsibility.

#### Disclaimer

The development team is not liable for loss caused by installing, using, or being unable to use ZrLog, except where required by law.`,
    },
    database: {
        title: "Connect database",
        description: "Enter connection details, test them, then set up the site.",
        dbType: "Database type",
        dbHost: "Host",
        dbName: "Database",
        dbUserName: "Username",
        dbPassword: "Password",
        dbPort: "Port",
        dbNameHelp: "If the database does not exist, ZrLog will try to create it.",
        dbUserHelp: "Avoid root for remote connections.",
        initRisk: "ZrLog tables and default content will be written here. An empty database is recommended.",
        testFailedTitle: "Database connection failed",
        testFailedAction: "Check host, port, account, password, and permissions, then retry.",
    },
    website: {
        title: "Set up site and admin",
        description: "Create the admin account and site name. You can change them later.",
        admin: "Admin account",
        adminPassword: "Admin password",
        adminEmail: "Admin email",
        siteTitle: "Site title",
        siteTitlePlaceholder: "Name your site",
        siteSubtitle: "Site subtitle",
        installAction: "Install",
        installing: "Installing",
        installIntro: "Progress appears after installation starts.",
    },
    probe: {
        title: "Environment",
        checking: "Checking environment",
        pass: "Environment looks good",
        warning: "Environment has notices",
        block: "Fix environment issues first",
        retry: "Check again",
        expand: "Details",
        item: {
            "runtime.mode": {
                title: "Runtime mode",
                pass: "Runtime mode: {value}",
                warning: "Runtime mode: {value}",
                block: "Runtime mode was not detected.",
            },
            "runtime.charset": {
                title: "System charset",
                pass: "System charset: {value}",
                warning: "Charset is not UTF-8. Text may be garbled.",
                block: "System charset looks invalid.",
            },
            "file.dbProperties": {
                title: "Database config file",
                pass: "Database config file is writable.",
                warning: "Database config file will be created during setup.",
                block: "Database config file is not writable. Check directory permissions.",
            },
            "file.installLock": {
                title: "Install lock file",
                pass: "Install lock file is writable.",
                warning: "Install lock file will be created during setup.",
                block: "Install lock file is not writable. Check directory permissions.",
            },
            "runtime.dockerMount": {
                title: "Docker persistence",
                pass: "Docker config directory is available.",
                warning: "Make sure the config directory is mounted, for example /opt/zrlog/conf.",
                block: "Docker config directory is not writable. Check volume mount and permissions.",
            },
            "runtime.faasAskConfig": {
                title: "Serverless config handoff",
                pass: "Serverless config check passed.",
                warning: "After setup, save the configuration shown on the completion page.",
                block: "Serverless configuration is unavailable.",
            },
        },
    },
    progress: {
        title: "Install progress",
        running: "Running",
        complete: "Complete",
        error: "Failed",
        item: {
            preflight: "Check environment",
            database: "Connect database",
            schema: "Initialize tables",
            "seed-website": "Write site settings",
            "seed-admin": "Create admin account",
            "seed-defaults": "Write default content",
            config: "Save installation config",
            install: "Install",
        },
    },
    error: {
        requestError: "Request failed",
        unknown: "Unknown error",
        db: {
            DB_NOT_EXISTS: "Database does not exist and automatic creation failed. Check the database name or create it manually.",
            CREATE_CONNECT_ERROR: "Cannot connect to database. Check host, port, container network, and database binding.",
            USERNAME_OR_PASSWORD_ERROR: "Username or password is incorrect, or the account has no remote access permission.",
            SQL_EXCEPTION_UNKNOWN: "Database returned an unknown error. Check database or application logs.",
            MISSING_JDBC_DRIVER: "JDBC driver is missing. The installation package may be incomplete.",
            UNKNOWN: "Database check failed. Check logs and retry.",
        },
    },
    success: {
        installSuccess: "Installation complete",
        viewSite: "View site",
        askConfigTitle: "Next configuration",
    },
    copyright: "Copyright © 2013-2026",
};

export type InstallI18nResource = typeof zhCN;

export const normalizeInstallLang = (lang?: string): InstallLang => (lang === "en_US" ? "en_US" : "zh_CN");

export const getInstallI18n = (lang?: string): InstallI18nResource => {
    return normalizeInstallLang(lang) === "en_US" ? enUS : zhCN;
};
