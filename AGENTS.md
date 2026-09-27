# AGENTS.md

这份文档是 AI Agent 在 `zrlog-install-web` 工程内工作的入口规则。进入本仓库后，先读本文件，再按任务打开 `DEVELOPMENT.md`、后端 controller/service、前端组件或 `zrlog-ops` 验收规则。

## 工程定位

`zrlog-install-web` 是 ZrLog 的安装初始化模块，主要负责：

- 首次安装页面和安装向导前端。
- 安装环境探测、数据库连接测试、初始化 SQL、首篇文章和系统配置写入。
- 安装 SSE 进度、安装锁和安装完成状态。
- 作为 `zrlog` 主工程发布包中的 install Web 模块被加载。

这里的目标是把“未安装 -> 可探测 -> 可填写 -> 可执行安装 -> 已安装锁定”的流程做稳定。不要为了本地验证绕过真实安装接口或手写最终配置文件。

## 目录职责

| 路径 | 职责 |
| --- | --- |
| `src/main/java/com/zrlog/install/web/controller/api/` | 安装 API、环境探测、数据库连接测试和 SSE 安装入口。 |
| `src/main/java/com/zrlog/install/web/controller/page/` | 安装页路由和页面入口。 |
| `src/main/java/com/zrlog/install/business/service/` | 安装业务、建表、初始化数据和配置写入。 |
| `src/main/java/com/zrlog/install/business/vo/` | 安装请求/配置对象和业务数据结构。 |
| `src/main/java/com/zrlog/install/web/interceptor/` | 安装状态、锁和请求拦截。 |
| `src/main/resources/install/` | 初始化 SQL 和安装资源。 |
| `src/main/resources/i18n/` | 安装流程后端资源和初始化文章资源。 |
| `src/main/frontend/` | React 安装向导，主要组件在 `src/components/index.tsx`。 |
| `conf/` | 本地安装配置和锁文件，通常不应提交调试变更。 |

## 必读文档

- [开发与扩展指南](DEVELOPMENT.md)
- `zrlog-ops/acceptance/zrlog-install-web.yaml`
- `zrlog-ops/docs/repository-structure-guide.md`
- `zrlog-ops/docs/ui-design-guide.md`

## 构建与验证

常用命令：

```bash
mvn -q test
cd src/main/frontend && yarn type-check
cd src/main/frontend && yarn build
mvn -q -PnodeBuild install
```

修改后端安装行为时至少运行 `mvn -q test`。修改前端向导时至少运行 `cd src/main/frontend && yarn type-check`，影响打包资源时运行 `yarn build` 或 `mvn -q -PnodeBuild install`。

## 边界规则

- 安装流程必须走真实 API 和 SSE，不用手写 `conf/db.properties` 或 `conf/install.lock` 代替验收。
- 数据库类型、默认端口、字段展示和后端参数读取必须一起检查，避免前后端只改一侧。
- 安装锁、环境探测、配置文件权限、SQL 初始化和异常消息属于安装核心链路，改动必须有测试或真实命令证据。
- 前端可见安装文案优先维护在 `src/main/frontend/src/i18n/install.ts`；初始化文章和后端安装资源维护在 `src/main/resources/i18n/`。
- `src/main/frontend/build`、`tsconfig.tsbuildinfo`、`target/`、本地 `conf/db.properties` 和 `conf/install.lock` 不应作为普通开发改动提交。
- 安装模块被 `zrlog` 主工程加载；改变 jar 资源、依赖 scope 或启动发现逻辑时，必须回到 `zrlog` 验证预览包安装链路。

## AI 修改流程

1. 先判断任务是后端安装协议、前端向导、初始化数据，还是主工程打包集成。
2. 读取真实 API、service、表单字段和 i18n 资源，不凭页面文案猜参数名。
3. 保留工作区已有用户改动，不 reset、restore 或覆盖无关文件。
4. 修改数据库或安装状态时覆盖成功、失败、重复安装和 warning/block 环境分支。
5. 修改 UI 时按 `zrlog-ops/docs/ui-design-guide.md` 做桌面和移动端验收。
6. 最终回复说明安装链路影响、验证命令、生成物清理结果和是否需要主工程预览包验证。

## 常见任务入口

| 任务 | 起点 |
| --- | --- |
| 安装 API 或 SSE | `src/main/java/com/zrlog/install/web/controller/api` |
| 安装业务和初始化 SQL | `src/main/java/com/zrlog/install/business/service`、`src/main/resources/install` |
| 数据库类型或连接测试 | `ApiInstallController`、前端 `getDefaultPort()` 和数据库表单项 |
| 安装页面 UI | `src/main/frontend/src/components/index.tsx` |
| 安装文案 | `src/main/frontend/src/i18n/install.ts` 和 `src/main/resources/i18n` |
| 主工程包内验证 | `zrlog-ops/acceptance/zrlog-preview-package-install.yaml` |

## 跨工程测试标准

遵守 `zrlog-ops/docs/test-case-writing-guide.md` 与 base 的 `docs/test-support.md`：临时目录、H2/SQLite、真实安装结果断言、资源及全局状态恢复。install-web 是 SQL schema 和安装流程的生产者，继续使用 common-dao 的 InMemoryDatabase 及本仓库的 typed 安装配置夹具，不反向依赖消费安装 schema 的 base 测试构件，避免 install → base → install 的构建环。安装用例不得使用预建 schema 或手写 install.lock 替代 InstallService。
