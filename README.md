# zrlog-install-web

`zrlog-install-web` 是 ZrLog 博客系统的 Web 安装向导，采用前后端分离架构实现系统的初始化配置功能。

## 核心功能

- **分离式架构**: 前端基于 React 18 与 Ant Design 6.x 开发，后端采用 SimpleWebServer 作为 HTTP 服务。
- **多数据库支持**: 
  - 支持 MySQL / MariaDB，具备 MySQL 数据库不存在时的自动建库功能。
  - ZIP / Native 环境在 SQLite 驱动和可写存储可用时，默认提供本地 SQLite 安装。
  - 支持 WebApi（基于 Cloudflare D1 转发 SQL 的 Serverless 存储架构）。
- **环境探测与恢复**: 安装前检查运行环境和配置目录，安装进度通过 SSE 展示，并提供失败恢复入口。
- **可扩展配置**: 支持在外部 JSON 配置文件中预置第三方服务（如 AI 模型 Key、静态页面预处理同步等参数），满足 FaaS 场景需求。

## 技术栈

| 模块 | 技术栈 |
| --- | --- |
| 后端 | Java 11, SimpleWebServer, MySQL Connector |
| 前端 | React 18, Ant Design 6.x, Axios |
| 构建 | Maven (frontend-maven-plugin), Yarn |

## 编译打包

通过 Maven 执行全量编译，将自动执行前端代码构建及后端 Jar 包合成：

```bash
mvn clean package -PnodeBuild,jar
```
编译产物位于项目根目录：`zrlog-install-web-starter.jar`。

## 运行与部署

标准运行模式：
```bash
java -jar zrlog-install-web-starter.jar
```

非交互式/携带外部全局配置运行（配置文件由调用方维护）：
```bash
java -jar zrlog-install-web-starter.jar /path/to/install-config.json ${YOUR_GITHUB_PAT}
```

演示站点的定时重置任务及环境配置由内部运维任务维护，本工程只提供安装程序源码和构建产物。

## 开发相关资源

关于该组件的二次开发机制与扩展说明，请阅读 [DEVELOPMENT.md](./DEVELOPMENT.md)。
关于项目的改进功能建议汇总，请参看 [IMPROVEMENTS.md](./IMPROVEMENTS.md)。
面向 AI Agent 和跨仓库开发的安装模块入口见 [AGENTS.md](AGENTS.md)。
