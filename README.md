# 黄村乡防溺水安全监管系统

本仓库由原 `HC_Drown_Plantform` 独立而来，Java 后端和 Vue 监管前端直接位于仓库根目录。

| 项目 | 仓库 | 默认端口 |
| --- | --- | --- |
| 防溺水安全监管系统（本仓库） | [VideoFlow_AI](https://github.com/weqdaw/VideoFlow_AI) | 后端 8080，前端 5173 |
| 视频分析与监控分发系统 | [drowning_detection_system](https://github.com/weqdaw/drowning_detection_system) | 8000 |
| Android 移动值守终端 | [drowning_detection_system_android](https://github.com/weqdaw/drowning_detection_system_android) | 连接业务后端 8080 |

```text
VideoFlow_AI/
├── src/                 # Spring Boot 业务后端、SQL 迁移、测试
├── frontend/            # Vue 3 + TypeScript + Cesium 监管前端
├── docs/                # 移动端 API、Postman 集合及设计文档
├── pom.xml
└── .env.example         # 环境变量示例
```

## 启动

准备 Java 21、Maven、Node.js 20+、PostgreSQL 15 + PostGIS、Redis 和 Kafka。创建 `hc_drown` 数据库并启用 PostGIS；迁移由 Flyway 执行。

复制 `.env.example` 为本机 `.env`，替换其中的占位值，然后在 PowerShell 加载配置：

```powershell
Copy-Item .env.example .env
notepad .env
Get-Content .env | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#')) {
        $pair = $line.Split('=', 2)
        [Environment]::SetEnvironmentVariable($pair[0], $pair[1], 'Process')
    }
}
mvn spring-boot:run
```

Spring Boot 不自动读取 `.env`。`JWT_SECRET` 至少 32 字节；数据库密码按本机设置；`DISTRIBUTION_API_TOKEN` 必须与监控分发系统一致。`DISTRIBUTION_URL` 默认为 `http://127.0.0.1:8000`。本机原有运行配置不随本次拆分改动。

在另一终端启动前端：

```powershell
cd frontend
npm ci
npm run dev
```

监管界面：`http://localhost:5173`；业务接口：`http://localhost:8080`；Swagger：`http://localhost:8080/swagger-ui.html`。视频流分析与分发仍由另一个仓库的 Python 服务提供。

## 同时运行两个系统

将两个仓库克隆到同一个父目录：

```powershell
git clone https://github.com/weqdaw/drowning_detection_system.git
git clone https://github.com/weqdaw/VideoFlow_AI.git
```

监控分发仓库中的 `start_system.bat` 会查找相邻的 `VideoFlow_AI`；也可通过 `HC_PLATFORM_DIR` 指定本仓库路径。分别手动运行服务时无需这种目录布局。

## 文档

- [后端设计与开发说明](docs/backend-guide.md)
- [监管前端说明](frontend/README.md)
- [移动端 API](docs/mobile-api.md)
- [仓库拆分记录](docs/repository-split.md)

原 VideoFlow_AI 项目完整保留在 [`archive/before-hc-split-20261023`](https://github.com/weqdaw/VideoFlow_AI/tree/archive/before-hc-split-20261023) 分支。本次使用普通提交更新 `main`，没有强制覆盖历史。
