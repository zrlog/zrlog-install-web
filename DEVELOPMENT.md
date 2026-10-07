# ZrLog Install Web 开发与扩展指南

UI 任务先读 [Ops UI 统一入口](../zrlog-ops/docs/ui-design-guide.md)，安装呈现规则见 [专项规范](../zrlog-ops/docs/material3-agent-guide.md#zrlog-install-web-安装视觉工程)，检查矩阵见 [安装契约](../zrlog-ops/acceptance/zrlog-install-web.yaml)。本文维护开发、接口与 i18n 实现，不保存产品视觉规则副本。

## 1. 工程目录结构

```text
.
├── pom.xml                   # Maven 构建文件定义 (含前端一体化构建配置)
├── conf/                     # 外部装载配置文件存放区
└── src/
    └── main/
        ├── frontend/         # 前端独立工程目录 (含 React / Yarn 相关文件)
        │   ├── package.json  
        │   ├── src/          # 核心 React 页面组件代码挂载于 components/index.tsx
        │   └── tsconfig.json 
        ├── java/.../install  # 后端源代码运行逻辑
        │   ├── business/     # 业务逻辑服务层 (建表及初始核心逻辑承载)
        │   ├── exception/    # 系统异常枚举与自定义
        │   └── web/          # Web 请求控制器
        └── resources/
            ├── i18n/         # 国际化语言包映射资源 (.properties)
            └── init-table-structure.sql # 数据库初始化及首次表结构配置脚本
```

## 2. 本地开发环境与调试方法

### 2.1 后端调试启动
1. 依赖 JDK 11 开发环境。
2. 内部采用 SimpleWebServer 轻量框架，在开发套件 (IDE) 中定位到主类 `com.zrlog.install.Application` 从而运行 `main` 方法进入调试态。

### 2.2 前端独立开发调试
使用 Node.js 22 LTS（至少 22.22.1）和 Yarn 1.x。Maven 的 `nodeBuild` 配置固定使用
Node.js 22.22.1；本地开发不再通过 npm 的 `node` 或平台专属二进制依赖覆盖运行时。
Husky 9 使用 `prepare` 脚本安装前端目录下的 Git hooks。

为支持页面实时重载（HMR），建议使用独立的前台端口服务：
```bash
cd src/main/frontend
yarn install
yarn start
```
*备注：前后端接口存在端口差异，如触发跨域控制限制，需在前后端代码间设置 Proxy 策略或调整 CORS 配置保证接口可达。*

## 3. 开发架构级应用修改与扩展

### 3.1 增加适配非默认数据库类型 (以 PostgreSQL 为例)

1. **依赖库扩充**: 引入 Maven `pom.xml` 中 PostgreSQL 对应的底层 JDBC 组件支持。
2. **后端驱动映射**: 在 `ApiInstallController.java` 的 `getDbConn()` 中为新的 `dbType` 映射驱动全类名（如 `org.postgresql.Driver`），并同步扩展 `InstallRequestValidator` 的数据库类型白名单与字段校验。如需自动创建数据库，在 `InstallService.java -> createDatabase()` 中按方言实现受约束的 DDL。
3. **前端表项拓展**: 修改向导组件 `src/main/frontend/src/components/index.tsx` 的 `Radio.Group` 存储方式选项、对应字段区和 `getDefaultPort()`。同时补充桌面、移动端以及浅色、深色主题验收。

### 3.2 自定义内置博文与系统模板

1. **基础参数与表定制**: 直接修改内置的全局 SQL 构建文件库：`src/main/resources/init-table-structure.sql`。
2. **第一篇文章呈现内容模板**: 直接修改 `src/main/resources/i18n/init-blog/` 路径下的 `.md` 和 `.html` 格式文件组合，最终通过 `InstallService.java` 进行模板处理并提交数据库执行。

### 3.3 扩展收集高阶属性参数 (接入第三方组件及平台验证要求等)

安装 mutation 接口统一采用 `POST application/json`，扩展字段时应同时维护前后端契约。默认不启用安装令牌；只有运行环境显式设置非空的 `ZRLOG_INSTALL_TOKEN` 后，后端才要求请求通过 `X-ZrLog-Install-Token` 携带相同值：
1. **添加 UI 节点**: 在对应表单步骤中增加 `FormItem` 与校验规则。数据库配置位于 `state.current === 0`，站点与管理员配置位于 `state.current === 1`，`state.current === 2` 是安装完成页。
2. **发送 JSON**: 将字段加入 `testDbConn` 或 `startInstall` 的 JSON body。启用安装令牌后，令牌只能通过 `X-ZrLog-Install-Token` 请求头传递；mutation URL 不允许携带 query 参数。
3. **接收与校验**: 在 `ApiInstallController.RequestParameters` 读取 JSON 原始值，并在 `InstallRequestValidator` 完成长度、字符集、枚举和组合约束，再映射到 `business/vo`。不得退回 `getParaToStr()` 或表单/query 参数提取。

`install.lock` 是安装完成后的重复安装保护，不要删除或用开发脚本绕过。它在首次安装完成前还不存在，因此默认关闭令牌的未安装实例如果直接暴露到公网，仍可能被他人抢先初始化。公网、共享网络或无人值守部署应在服务公开前配置高强度且稳定的 `ZRLOG_INSTALL_TOKEN`；FaaS 的所有冷启动和并发实例必须使用同一个配置值，不能在每次冷启动时临时生成。

## 4. 国际化 (i18n) 注意规范

表达规则与产品例外统一读取 [Ops 产品文案规范](../zrlog-ops/docs/content-writing-guide.md)，包括 [安装页脚版权](../zrlog-ops/docs/content-writing-guide.md#安装页脚版权)。本节只维护资源归属和集成方式。

页脚 ZrLog 品牌链接指向官网首页，保留固定 UTM 来源参数：`utm_source=zrlog`、
`utm_medium=referral`、`utm_content=install-footer`，用于识别安装页版权区的访问来源。

安装页资源由前端类型化文案与后端运行时资源合并，交互文案不得直接硬编码在 React 组件中。
1. 页面、表单、恢复和进度文案维护在 `src/main/frontend/src/i18n/install.ts`，中英文结构必须保持一致，并通过 `getRes()` 读取。
2. 后端异常、SSE 失败和服务端模板文案维护在 `src/main/resources/i18n/install_*.properties`；新增错误码时应同步后端测试与前端错误映射。
3. 修改任一资源后至少运行前端测试、`yarn type-check`、`yarn build` 与后端测试，确认 SSR 注入和运行时 API 合并结果一致。

## 5. 前端公共包

安装器通过 npmjs 的固定版本依赖和 yarn.lock 消费 [zrlog-frontend-common](../zrlog-frontend-common/README.md) 的 `@zrlog/ui` / `@zrlog/utils`；不复制后台主题源码。公共源码修改先在公共仓库发布版本，再升级这里的版本与锁文件。

- `theme/install-theme.ts` 使用 `@zrlog/ui/material` 的 `createMaterialTheme`，只加载 M3；默认主色来自 `@zrlog/ui/themes`，该轻量入口不加载 React 或样式运行时。
- `index.tsx` 在 ConfigProvider 内挂载 `MaterialStyles`；品牌组件由本地 `ZrLogMark` 包装共享实现。React、React DOM、Ant Design 由安装器提供一份运行时。
- `UiIconProvider theme="default"` 包裹配置根。页面图标从 `@zrlog/ui/material-icons/<语义名>` 逐个导入，存储方式和明暗选择把选中态传给 `selected`；不会加载后台其他主题的图形。组件级图标槽使用 `@zrlog/ui/antd/<Component>`，状态消息使用 `@zrlog/ui/feedback`。
- 图标 API 使用已发布的 `@zrlog/ui` / `@zrlog/utils` 0.2.0；package.json 固定版本，yarn.lock 记录 npm registry 的下载地址与完整性校验值。
- `utils/env-utils.tsx` 仍管理 light / dark / system 的保存与首次浅色策略，安装器不提供后台多主题选择。
- 安装和升级使用 `@zrlog/utils` 的 `readJsonSseStream` 解码。请求 URL、安装令牌、进度文案、终止事件和错误恢复仍由本工程负责。

本地联调可临时安装公共仓库 `npm run pack` 产生的 tarball；不得提交本机绝对路径依赖。正式接入前验证版本包下载与校验值，再执行类型、测试、生产构建和真实安装 SSE 验收。

## 发布认证

Maven 3.10 起会按仓库 origin 校验凭证；`central` 默认关联的下载域名与 Sonatype 发布域名不同。发布工作流读取实际 Maven 版本，3.10 及以上使用 settings 1.3.0，并为 `central` 显式声明 `https://central.sonatype.com`；旧 Maven 保留 settings 1.0.0，避免不支持 `repositoryOrigins` 的警告。升级 Maven 时须验证 HTTP 认证上传，文件仓库部署无法覆盖凭证校验。
