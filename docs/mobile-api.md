# 黄村乡防溺水系统 · 移动端接口文档

版本：2026-10-20。适用于 Android、iOS、Flutter、uni-app 以及 H5 对接。

## 1. 接入地址与约定

| 项目 | 值 |
| --- | --- |
| 当前电脑局域网 Base URL | `http://192.168.0.111:8080` |
| 本机开发 Base URL | `http://127.0.0.1:8080` |
| 风险任务 API | `/api/v1/mobile/risk-tasks` |
| 在线交互文档 | `/swagger-ui/index.html` |
| 实时 OpenAPI JSON | `/v3/api-docs` |
| 可导入的 OpenAPI 文件 | [mobile-api.openapi.json](mobile-api.openapi.json) |
| Postman 集合 | [mobile-api.postman_collection.json](mobile-api.postman_collection.json) |
| 现有全部业务接口索引及参数 | [existing-api-reference.md](existing-api-reference.md) |

手机与服务器在同一局域网时，使用电脑真实网卡 IP；手机中的 `127.0.0.1` 指向手机自身。电脑地址变化后替换 Base URL。服务监听地址默认 `0.0.0.0:8080`，可使用 `SERVER_ADDRESS` 配置；网络需允许手机访问 TCP 8080。跨公网部署时，使用 HTTPS 域名反向代理到此服务。

任务查询、人工标注、处理历史、截图及视频均使用 8080。Python 8000 由 Java 在服务器内部访问，移动端不需要拼接 8000 的证据路径。本文的“全部任务功能”包括新增页面的完整业务；既有监管、设备、事件、录像、人员、救援、统计与管理 API 见附带索引。实时摄像头 WebSocket 入口另见第 10 节。

请求和 JSON 响应使用 UTF-8。POST JSON 设置 `Content-Type: application/json`。所有时间字符串格式为 `yyyy-MM-dd HH:mm:ss`，按北京时间（UTC+8）解释。`expiresAt` 为 Unix 秒。ID 为 64 位整数，客户端应使用能完整保存 64 位 ID 的类型。

## 2. 登录与权限

### 2.1 登录

`POST /api/v1/auth/login`，无需 Bearer。使用实际分配的账号密码：

```json
{"username":"your_username","password":"your_password"}
```

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {"token":"<JWT>","tokenHead":"Bearer "}
}
```

后续业务请求添加：

```http
Authorization: Bearer <JWT>
```

`GET /api/v1/auth/info` 返回当前用户资料以及 `roles`、`permissions`。令牌有效期由服务端 `jwt.access-token-expiration` 配置，当前默认 7200 秒。收到 401 时重新登录；当前未实现 refresh 接口。`POST /api/v1/auth/logout` 后客户端删除本地令牌，当前为无服务端撤销名单的 JWT 登录方式。

| 能力 | 所需权限 |
| --- | --- |
| 任务列表、详情、统计、历史、生成媒体链接 | `task:read` |
| 标注已处理或识别错误 | `task:update` 或 `alert:confirm` 任一项 |
| 直接访问截图/视频 | `task:read` 的 Bearer，或有效的短时媒体签名 |

业务数据由接口按账户已有权限返回；登录成功不代表拥有全部写权限。移动端可通过 `auth/info` 的权限列表控制操作按钮。

## 3. 风险任务接口清单

以下相对路径均以 `/api/v1/mobile/risk-tasks` 为前缀。大屏路径 `/api/v1/risk-tasks` 也保留，移动端统一使用本文路径。

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| GET | 空路径 | 分页与组合筛选 |
| GET | `/stats` | 全量状态数量 |
| GET | `/{id}` | 任务详情，包含完整处理历史 |
| POST | `/{id}/status` | 人工标注 |
| GET | `/{id}/history` | 单独读取处理历史 |
| GET | `/{id}/media-links` | 获取 5 分钟有效的媒体链接 |
| GET / HEAD | `/{id}/snapshot` | 带框截图原图 / 元信息 |
| GET / HEAD | `/{id}/video` | MP4 回放、下载 / 元信息 |

## 4. 分页与筛选

`GET /api/v1/mobile/risk-tasks`

| Query 参数 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `pageNum` | integer | 1 | 从 1 开始 |
| `pageSize` | integer | 12 | 1–100 |
| `status` | string | 省略 | `PENDING` / `PROCESSED` / `RECOGNITION_ERROR`；全部任务时不要传 `ALL` |
| `deviceCode` | string | 省略 | 设备编码精确匹配，不区分大小写 |
| `keyword` | string | 省略 | 告警名称、设备名称、地点名称的模糊查询，最多 200 字；`%`、`_` 按文字匹配 |
| `startTime` | string | 省略 | 告警时间下限，包含边界 |
| `endTime` | string | 省略 | 告警时间上限，包含边界；不能早于 startTime |

筛选条件按 AND 组合。时间中的空格和中文应由 HTTP 库进行 URL 编码。结果按告警时间降序、ID 降序排列。

```http
GET /api/v1/mobile/risk-tasks?pageNum=1&pageSize=12&status=PENDING&deviceCode=camera-a
Authorization: Bearer <JWT>
```

响应结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [
      {
        "id": 1,
        "incidentKey": "1234567890abcdef1234567890abcdef",
        "streamId": "camera-a",
        "eventName": "人员意外落水/溺水事故",
        "details": "警告！AI算法识别到溺水姿态",
        "confidence": 0.87,
        "occurredAt": "2026-10-20 15:00:00",
        "sourceDeviceId": 17,
        "sourceDeviceName": "东岸监控点",
        "sourceDeviceCode": "camera-a",
        "sourceDeviceType": "CAMERA",
        "deviceIp": "192.0.2.1",
        "selectedModel": "check1.pt",
        "location": "POINT(120.1 28.2)",
        "locationDesc": "测试水域东岸",
        "snapshotUrl": "/api/v1/mobile/risk-tasks/1/snapshot",
        "videoUrl": "/api/v1/mobile/risk-tasks/1/video",
        "snapshotApiUrl": "/api/v1/mobile/risk-tasks/1/snapshot",
        "videoApiUrl": "/api/v1/mobile/risk-tasks/1/video",
        "mediaLinksUrl": "/api/v1/mobile/risk-tasks/1/media-links",
        "mediaStatus": "READY",
        "mediaError": null,
        "status": "PENDING",
        "handledBy": null,
        "handledAt": null,
        "note": null
      }
    ],
    "total": 1,
    "current": 1,
    "size": 12,
    "counts": {"total":1,"pending":1,"processed":0,"error":0}
  }
}
```

示例 ID、设备及数量仅展示格式。`total` 是当前筛选结果总量；`counts` 始终是全部历史任务的状态统计，不随筛选和分页改变。空结果为 `records: []`、`total: 0`。

### 4.1 任务字段

| 字段 | 含义 |
| --- | --- |
| `id` | 风险任务 ID，不是救援派单 ID、Python 日志 ID 或设备 ID |
| `incidentKey` | 唯一来源告警键，用于同步去重 |
| `streamId` | 监控分发通道 ID |
| `eventName` / `details` | 告警名称 / 检测说明 |
| `confidence` | 检测置信度，通常 0–1；可能为空 |
| `occurredAt` | 实际告警时间 |
| `sourceDeviceId/Name/Code/Type` | 告警时保存的设备信息；未匹配设备时 ID 可为空 |
| `deviceIp` / `selectedModel` | 当时的设备 IP / 检测模型，可能为空 |
| `location` | 当时的 WKT 坐标，`POINT(经度 纬度)`，可能为空 |
| `locationDesc` | 当时的地点名称，可能为空 |
| `snapshotUrl` / `snapshotApiUrl` | 8080 鉴权截图相对地址；无截图时为空 |
| `videoUrl` / `videoApiUrl` | 8080 鉴权视频相对地址；未归档或失败时为空 |
| `mediaLinksUrl` | 获取无需自定义请求头的短时媒体链接 |
| `mediaStatus` | `RECORDING` 录制中、`READY` 已归档、`FAILED` 留存失败 |
| `mediaError` | 失败诊断信息，正常为空；界面建议显示简短的失败提示 |
| `status` | `PENDING` 未处理、`PROCESSED` 已处理、`RECOGNITION_ERROR` 识别错误 |
| `handledBy` / `handledAt` / `note` | 最近一次处理人 / 时间 / 备注，未处理时为空 |

### 4.2 独立统计

`GET /api/v1/mobile/risk-tasks/stats`，`data` 直接为 `{"total":1,"pending":1,"processed":0,"error":0}`。

## 5. 详情和历史记录

`GET /api/v1/mobile/risk-tasks/{id}` 返回第 4 节的单个任务对象，并增加 `history` 数组。任务不存在返回 HTTP 404。

`GET /api/v1/mobile/risk-tasks/{id}/history` 的 `data` 直接为历史数组，按生成顺序排列：

```json
[
  {"id":1,"fromStatus":null,"toStatus":"PENDING","operatorName":"AI 风险检测","note":"安全动态监测触发红标告警，自动创建任务","createdAt":"2026-10-20 15:00:03"},
  {"id":2,"fromStatus":"PENDING","toStatus":"PROCESSED","operatorName":"值守员","note":"已现场核验，人员安全","createdAt":"2026-10-20 15:05:00"}
]
```

历史包含自动建单及人工状态变化。设备后续更名、移动或状态变化，不覆盖任务中已保存的现场信息。刷新、服务重启后仍可读取任务和处理历史。

## 6. 人工标注

`POST /api/v1/mobile/risk-tasks/{id}/status`

```json
{"status":"PROCESSED","expectedStatus":"PENDING","note":"已现场核验，人员安全"}
```

| Body 字段 | 必填 | 说明 |
| --- | --- | --- |
| `status` | 是 | 只接受 `PROCESSED` 或 `RECOGNITION_ERROR` |
| `expectedStatus` | 是 | 页面上次读取的原状态，三种任务状态之一 |
| `note` | 否 | 处理备注，最多 1000 字 |

成功返回 `{"code":200,"message":"操作成功","data":true}`。标注识别错误时使用：

```json
{"status":"RECOGNITION_ERROR","expectedStatus":"PENDING","note":"复核为水面反光"}
```

处理人和处理时间由服务端根据当前账号填写，客户端不能冒填。任务已被其他人更改时返回 HTTP 409；重新读取详情并由用户确认后再提交。重复提交与当前状态完全相同的目标状态为幂等成功，不重复添加历史；仅修改备注而不改变状态不会新增处理记录。允许在两种已标注状态之间重新核验，每次变化均留痕。此接口不支持将任务重新改回未处理。

## 7. 截图与视频：只使用 8080

### 7.1 可以设置请求头的客户端

直接以 Base URL 拼接任务中的 `snapshotUrl` 或 `videoUrl`，添加 Bearer。响应为 JPEG / H.264 MP4 **二进制**，不使用 JSON 包装。

```http
GET /api/v1/mobile/risk-tasks/1/video
Authorization: Bearer <JWT>
Range: bytes=0-1048575
```

完整文件返回 200；有效字节范围返回 206，并保留 `Content-Range`、`Content-Length`、`Accept-Ranges`。仅支持单段 Range（如 `bytes=0-1023`、`bytes=1024-`、`bytes=-1024`），范围超出文件返回 416。`HEAD` 可获取元信息，访问权限要求相同。

添加 `?download=true` 返回附件下载响应，例如 `/1/video?download=true`。服务端以流方式转发文件，不将整个视频一次性装入内存。

### 7.2 图片/播放器组件不能设置 Authorization 时

先以 Bearer 请求 `GET /api/v1/mobile/risk-tasks/{id}/media-links`：

```json
{
  "code":200,
  "message":"操作成功",
  "data":{
    "expiresAt":1792480200,
    "expiresIn":300,
    "mediaStatus":"READY",
    "snapshotUrl":"/api/v1/mobile/risk-tasks/1/snapshot?access=<短时签名>",
    "videoUrl":"/api/v1/mobile/risk-tasks/1/video?access=<短时签名>"
  }
}
```

把 `Base URL + data.snapshotUrl` 或 `Base URL + data.videoUrl` 交给媒体组件即可。签名绑定单个任务和媒体类型，有效期 5 分钟；过期后重新获取。此签名不能用于其他任务、任务查询或状态修改。需要下载时，在签名链接末尾追加 `&download=true`。这些链接包含临时访问能力，应仅交给需要播放的客户端。

`expiresAt` 示例只是格式，实际以响应为准。录制中 `videoUrl` 为 null，稍后重新获取。视频留存失败不删除任务、截图和人工历史。

### 7.3 告警与媒体时序

红标触发后自动保存现场带框截图，Java 通常在下一次 3 秒同步后查询到任务。持续告警合并；连续 5 秒没有红标后再次出现会产生新任务。每段视频最长 30 秒；任务可先查询/标注，视频完成后自动归档为 READY，不覆盖人工状态。

页面前台建议每 3–5 秒刷新任务或详情；进入后台后暂停轮询。`RECORDING` 时显示录制中，`READY` 时提供回放，`FAILED` 时显示留存失败并保留已有截图。不要在移动端额外创建风险任务，否则会与服务器自动建单重复。

## 8. 错误处理

新增风险任务接口的业务/参数错误同时使用对应 HTTP 状态和 JSON `code`：

```json
{"code":409,"message":"任务已被其他用户更新，请刷新后重试","data":null}
```

| HTTP / code | 场景 | 客户端处理 |
| --- | --- | --- |
| 200 | 成功 | 读取 data |
| 400 | 无效分页/状态/时间、JSON 格式错误、缺少必填 Body 字段、备注过长、多段 Range | 修正请求 |
| 401 | 未登录、JWT 过期、媒体签名无效/过期 | 重新登录或重新获取媒体链接 |
| 403 | 无相应权限 | 按权限隐藏操作或联系管理员分配 |
| 404 | 任务不存在、截图/视频不存在或暂不可用 | 刷新任务详情并显示缺失提示 |
| 409 | 标注版本冲突，或视频仍在录制 | 刷新后确认，或等待归档 |
| 416 | Range 超出文件长度 | 根据 Content-Range 重新请求 |
| 502 | 内部证据服务连接失败或返回异常 | 稍后重试，任务记录仍在 |

媒体成功响应以及 416 不使用 JSON 包装。既有其他业务接口沿用其原有约定，部分错误使用 HTTP 200 加 `code:400/1000/500`；接入时同时检查 HTTP 状态和 JSON code。

## 9. 移动端接入示例

以下为 uni-app 请求示例，Base URL 按部署地址替换：

```javascript
const baseUrl = 'http://192.168.0.111:8080';
const token = uni.getStorageSync('token');

uni.request({
  url: baseUrl + '/api/v1/mobile/risk-tasks',
  method: 'GET',
  header: { Authorization: 'Bearer ' + token },
  data: { pageNum: 1, pageSize: 12, status: 'PENDING' },
  success({ statusCode, data }) {
    if (statusCode === 200 && data.code === 200) {
      console.log(data.data.records, data.data.counts);
    }
  }
});

// 将任务列表中读取的 id 与 status 传入，勿在客户端猜测原状态。
function markProcessed(task, note) {
  return uni.request({
    url: baseUrl + `/api/v1/mobile/risk-tasks/${task.id}/status`,
    method: 'POST',
    header: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' },
    data: { status: 'PROCESSED', expectedStatus: task.status, note }
  });
}

// 对不支持自定义请求头的 <image> / <video>，先请求 media-links。
function loadMedia(taskId) {
  return uni.request({
    url: baseUrl + `/api/v1/mobile/risk-tasks/${taskId}/media-links`,
    header: { Authorization: 'Bearer ' + token },
    success({ data }) {
      if (data.code === 200) {
        const imageSrc = data.data.snapshotUrl ? baseUrl + data.data.snapshotUrl : '';
        const videoSrc = data.data.videoUrl ? baseUrl + data.data.videoUrl : '';
        // 将 imageSrc / videoSrc 赋给组件；5分钟后重新获取。
      }
    }
  });
}
```

## 10. 其他已有功能

完整方法、路径、权限及参数见 [现有接口参考](existing-api-reference.md)，字段结构见 OpenAPI；无需为移动端再建立一套数据库或重新实现业务。主要入口：

| 功能 | 8080 入口 |
| --- | --- |
| 登录、用户资料、登出、注册 | `/api/v1/auth/*` |
| 设备查询/管理、模型切换、检测阈值 | `/api/v1/devices/*` |
| 水域、电子围栏 | `/api/v1/water-areas/*` |
| 安全动态检测历史 | `/api/v1/alerts/detection-history` |
| 原有事件中心、人工确认 | `/api/v1/alerts/*` |
| 原有录像中心清单/管理 | `/api/v1/alerts/recordings` |
| 新增风险检测任务的全部功能 | `/api/v1/mobile/risk-tasks/*` |
| 救援派单、接单、到场、结果、定位上报 | `/api/v1/tasks/*` |
| 人员管理、在岗状态、通知、人员定位 | `/api/v1/users/*` |
| 统计总览、趋势、水域分布 | `/api/v1/statistics/*` |
| 审计日志、存储配置 | `/api/v1/admin/*` |

实时摄像头画面是现有独立协议：`ws://<服务器IP>:8080/api/ws/stream/{deviceId}`，输出 MPEG-TS / MPEG-1 二进制，当前大屏使用 JSMpeg；移动端需要相应解码能力。它不是本次任务的 H.264 MP4 告警回放接口，不应直接传给普通 MP4 播放器。

Python 监控分发服务的底层模型文件上传、蓝图编辑等运维接口属于另一套服务；本次提供的是新风险任务页面及现有监管业务的移动端调用合同，未将任意 Python 运维接口透明映射到 8080。

## 总览定位与 Web 地图联动

复用现有接口，无需数据库迁移或新增端口：

- `POST /api/v1/tasks/location`：Bearer JWT、`task:update` 权限，JSON 示例 `{"longitude":116.391,"latitude":39.907,"accuracy":5}`；经纬度为原始 WGS84 GPS，精度单位米。用户身份由 JWT 决定，不传 `userId`。真实调用会写入该账号的定位历史，示例坐标仅供专用测试账号使用。
- `GET /api/v1/users/active-locations`：登录后返回人员数组，使用 `id`/`username`、`realName`、`dutyStatus`、`status`、`lastWktLocation`。位置是 `POINT(经度 纬度)`；列表包含有历史位置的离岗人员，不能把每个返回项都理解成实时在线人员。
- 移动端总览前台获取 GPS；进入值守并获得权限后前台服务约每 10 秒上报，离岗/退出登录停止自动上报。移动端约每 5 秒、Web 约每 1 秒查询同一个位置列表；地图绘制人员时转换到高德底图坐标，转换结果不能回传作为 GPS。
- 当前位置列表没有每人的 `recordedAt`，客户端只显示“最近上报位置”。页面刷新时间表示查询完成，不能证明每个位置刚刚采集。超过两分钟的设备旧定位不自动上传。
- 当前项目原百度 Android Key 与调试签名不匹配；新增总览地图使用打包在 APK 的 Leaflet 及高德瓦片。接口联动不依赖百度 Key，底图加载需要可访问互联网。

现有 OpenAPI 为接口形状来源；本节补充客户端坐标约定和联动流程，Postman 集合保留原请求与鉴权，只修正位置示例。自动测试的 HTTP 服务是本机隔离测试环境；尚未替代真实账号的 GPS 上报、数据库落库和 Web 显示联调。
