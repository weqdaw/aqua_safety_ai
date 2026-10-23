# 现有业务 API 完整索引

由当前运行的 `/v3/api-docs` 和控制器权限注解生成。新增风险任务的完整接入合同见 [移动端接口文档](mobile-api.md)。

所有路径使用 8080。除登录/注册、现有公开资源及有效签名的任务媒体外，需要 Bearer。此处列出现有监管业务；类型的完整嵌套结构可在 [OpenAPI](mobile-api.openapi.json) 的 components.schemas 查看。

## GET `/api/v1/admin/audit-logs`

查询日志审计记录 (UC21)

**权限**：hasAuthority('audit:read')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `pageNum` | query | integer/int32 | 否 | 1 |
| `pageSize` | query | integer/int32 | 否 | 10 |
| `module` | query | string | 否 |  |
| `username` | query | string | 否 |  |
| `result` | query | string | 否 |  |

**响应模型**：`ResultPageAuditLog`；JSON 包含 `code`、`message`、`data`。

## POST `/api/v1/admin/audit-logs`

写入操作日志审计 (UC21)

**权限**：登录；未另设方法权限

**JSON 请求体**：`AuditLog`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | integer/int64 | 否 |  |
| `userId` | integer/int64 | 否 |  |
| `username` | string | 否 |  |
| `module` | string | 否 |  |
| `action` | string | 否 |  |
| `targetId` | string | 否 |  |
| `detail` | string | 否 |  |
| `ipAddress` | string | 否 |  |
| `result` | string | 否 |  |
| `createdAt` | string/date-time | 否 |  |

**响应模型**：`ResultBoolean`；JSON 包含 `code`、`message`、`data`。

## GET `/api/v1/admin/storage-configs`

获取存储配置策略 (UC22)

**权限**：hasAuthority('storage:config')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `waterAreaId` | query | integer/int64 | 否 |  |

**响应模型**：`ResultStorageConfig`；JSON 包含 `code`、`message`、`data`。

## POST `/api/v1/admin/storage-configs`

设置存储清理策略 (UC22)

**权限**：hasAuthority('storage:config')

**JSON 请求体**：`StorageConfig`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | integer/int64 | 否 |  |
| `waterAreaId` | integer/int64 | 否 |  |
| `retentionDaysVideo` | integer/int32 | 否 |  |
| `retentionDaysSnapshot` | integer/int32 | 否 |  |
| `backupEnabled` | boolean | 否 |  |
| `backupTarget` | string | 否 |  |
| `backupCron` | string | 否 |  |
| `storageWarnThreshold` | integer/int32 | 否 |  |
| `updatedBy` | integer/int64 | 否 |  |
| `updatedAt` | string/date-time | 否 |  |

**响应模型**：`ResultBoolean`；JSON 包含 `code`、`message`、`data`。

## GET `/api/v1/alerts`

分页获取报警列表 (UC01)

**权限**：hasAuthority('alert:read')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `pageNum` | query | integer/int32 | 否 | 1 |
| `pageSize` | query | integer/int32 | 否 | 10 |
| `status` | query | string | 否 |  |
| `waterAreaId` | query | integer/int64 | 否 |  |
| `alertType` | query | string | 否 |  |

**响应模型**：`ResultPageAlertEvent`；JSON 包含 `code`、`message`、`data`。

## DELETE `/api/v1/alerts/detection-history`

清空所有智能目标分析历史记录

**权限**：hasAuthority('alert:close')

**响应模型**：`ResultBoolean`；JSON 包含 `code`、`message`、`data`。

## GET `/api/v1/alerts/detection-history`

获取智能目标分析历史记录

**权限**：hasAuthority('alert:read')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `limit` | query | integer/int32 | 否 | 100 |

**响应模型**：`ResultListMapStringObject`；JSON 包含 `code`、`message`、`data`。

## DELETE `/api/v1/alerts/detection-history/{id}`

删除单条智能目标分析历史记录

**权限**：hasAuthority('alert:close')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer/int32 | 是 |  |

**响应模型**：`ResultBoolean`；JSON 包含 `code`、`message`、`data`。

## GET `/api/v1/alerts/mobile/list`

移动端专享：获取告警事件列表 (支持待处理/已处理状态筛选)

**权限**：登录；未另设方法权限

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `pageNum` | query | integer/int32 | 否 | 1 |
| `pageSize` | query | integer/int32 | 否 | 20 |
| `status` | query | string | 否 |  |

**响应模型**：`ResultPageAlertEvent`；JSON 包含 `code`、`message`、`data`。

## POST `/api/v1/alerts/mobile/{id}/process`

移动端专享：标记告警事件为已处理

**权限**：登录；未另设方法权限

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer/int64 | 是 |  |
| `handler` | query | string | 否 | MobileUser |

**响应模型**：`ResultBoolean`；JSON 包含 `code`、`message`、`data`。

## DELETE `/api/v1/alerts/recordings`

清空所有触发阈值抓拍录像记录及物理文件

**权限**：hasAuthority('alert:close')

**响应模型**：`ResultBoolean`；JSON 包含 `code`、`message`、`data`。

## GET `/api/v1/alerts/recordings`

获取阈值触发抓拍录像列表

**权限**：hasAuthority('alert:read')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `streamId` | query | string | 否 |  |
| `limit` | query | integer/int32 | 否 | 100 |

**响应模型**：`ResultListMapStringObject`；JSON 包含 `code`、`message`、`data`。

## DELETE `/api/v1/alerts/recordings/{id}`

删除指定的录像文件及记录

**权限**：hasAuthority('alert:close')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer/int32 | 是 |  |

**响应模型**：`ResultBoolean`；JSON 包含 `code`、`message`、`data`。

## POST `/api/v1/alerts/trigger`

接收AI主动预警上报 (模拟/触发入口)

**权限**：登录；未另设方法权限

**JSON 请求体**：`AlertEvent`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | integer/int64 | 否 |  |
| `eventNo` | string | 否 |  |
| `sourceDeviceId` | integer/int64 | 否 |  |
| `waterAreaId` | integer/int64 | 否 |  |
| `alertLocation` | string | 否 |  |
| `confidence` | number | 否 |  |
| `alertType` | string | 否 |  |
| `status` | string | 否 |  |
| `snapshotUrl` | string | 否 |  |
| `videoClipUrl` | string | 否 |  |
| `confirmedBy` | integer/int64 | 否 |  |
| `confirmedAt` | string/date-time | 否 |  |
| `falseAlarmReason` | string | 否 |  |
| `resolvedAt` | string/date-time | 否 |  |
| `createdAt` | string/date-time | 否 |  |
| `updatedAt` | string/date-time | 否 |  |
| `deleted` | integer/int32 | 否 |  |
| `longitude` | number/double | 否 |  |
| `latitude` | number/double | 否 |  |
| `sourceDeviceName` | string | 否 |  |
| `waterAreaName` | string | 否 |  |
| `confirmedByName` | string | 否 |  |

**响应模型**：`ResultAlertEvent`；JSON 包含 `code`、`message`、`data`。

## DELETE `/api/v1/alerts/{id}`

逻辑删除报警事件记录

**权限**：hasAuthority('alert:close')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer/int64 | 是 |  |

**响应模型**：`ResultBoolean`；JSON 包含 `code`、`message`、`data`。

## GET `/api/v1/alerts/{id}`

获取报警详情 (UC01)

**权限**：hasAuthority('alert:read')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer/int64 | 是 |  |

**响应模型**：`ResultAlertEvent`；JSON 包含 `code`、`message`、`data`。

## POST `/api/v1/alerts/{id}/confirm`

值守员人工确认报警 (UC02)

**权限**：hasAuthority('alert:confirm')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer/int64 | 是 |  |

**JSON 请求体**：`ConfirmParam`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `isReal` | boolean | 否 |  |
| `reason` | string | 否 |  |

**响应模型**：`ResultBoolean`；JSON 包含 `code`、`message`、`data`。

## POST `/api/v1/alerts/{id}/status`

大屏任务中心：更新任务事件状态 (PENDING / PROCESSED / RECOGNITION_ERROR)

**权限**：登录；未另设方法权限

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer/int64 | 是 |  |
| `status` | query | string | 是 |  |

**响应模型**：`ResultBoolean`；JSON 包含 `code`、`message`、`data`。

## GET `/api/v1/auth/info`

获取当前登录用户信息

**权限**：登录；未另设方法权限

**响应模型**：`ResultMapStringObject`；JSON 包含 `code`、`message`、`data`。

## POST `/api/v1/auth/login`

用户登录

**权限**：公开，无需Bearer

**JSON 请求体**：`LoginParam`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | string | 是 |  |
| `password` | string | 是 |  |

**响应模型**：`ResultMapStringString`；JSON 包含 `code`、`message`、`data`。

## POST `/api/v1/auth/logout`

用户登出

**权限**：登录；未另设方法权限

**响应模型**：`ResultVoid`；JSON 包含 `code`、`message`、`data`。

## POST `/api/v1/auth/register`

用户注册

**权限**：公开，无需Bearer

**JSON 请求体**：`RegisterParam`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | string | 是 |  |
| `password` | string | 是 |  |
| `guardId` | string | 否 |  |
| `realName` | string | 是 |  |
| `phone` | string | 否 |  |
| `roleCode` | string | 否 |  |

**响应模型**：`ResultMapStringObject`；JSON 包含 `code`、`message`、`data`。

## GET `/api/v1/devices`

分页获取设备列表 (UC18)

**权限**：hasAuthority('device:read')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `pageNum` | query | integer/int32 | 否 | 1 |
| `pageSize` | query | integer/int32 | 否 | 10 |
| `keyword` | query | string | 否 |  |
| `type` | query | string | 否 |  |
| `waterAreaId` | query | integer/int64 | 否 |  |

**响应模型**：`ResultPageDevice`；JSON 包含 `code`、`message`、`data`。

## POST `/api/v1/devices`

设备注册 (UC18)

**权限**：hasAuthority('device:write')

**JSON 请求体**：`Device`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | integer/int64 | 否 |  |
| `deviceCode` | string | 否 |  |
| `deviceName` | string | 否 |  |
| `deviceType` | string | 否 |  |
| `manufacturer` | string | 否 |  |
| `model` | string | 否 |  |
| `ipAddress` | string | 否 |  |
| `port` | integer/int32 | 否 |  |
| `location` | string | 否 |  |
| `locationDesc` | string | 否 |  |
| `waterAreaId` | integer/int64 | 否 |  |
| `status` | string | 否 |  |
| `lastHeartbeat` | string/date-time | 否 |  |
| `extraConfig` | object | 否 |  |
| `createdAt` | string/date-time | 否 |  |
| `updatedAt` | string/date-time | 否 |  |
| `deleted` | integer/int32 | 否 |  |
| `longitude` | number/double | 否 |  |
| `latitude` | number/double | 否 |  |
| `waterAreaName` | string | 否 |  |

**响应模型**：`ResultBoolean`；JSON 包含 `code`、`message`、`data`。

## POST `/api/v1/devices/heartbeat`

设备上报心跳/状态

**权限**：登录；未另设方法权限

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `deviceCode` | query | string | 是 |  |
| `status` | query | string | 是 |  |

**响应模型**：`ResultBoolean`；JSON 包含 `code`、`message`、`data`。

## GET `/api/v1/devices/nearest`

查询最近的特定类型设备 (联动推荐最近音柱等)

**权限**：hasAuthority('device:read')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `lng` | query | number/double | 是 |  |
| `lat` | query | number/double | 是 |  |
| `type` | query | string | 是 |  |

**响应模型**：`ResultDevice`；JSON 包含 `code`、`message`、`data`。

## DELETE `/api/v1/devices/{id}`

删除设备 (UC18)

**权限**：hasAuthority('device:delete')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer/int64 | 是 |  |

**响应模型**：`ResultBoolean`；JSON 包含 `code`、`message`、`data`。

## GET `/api/v1/devices/{id}`

获取设备详情

**权限**：hasAuthority('device:read')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer/int64 | 是 |  |

**响应模型**：`ResultDevice`；JSON 包含 `code`、`message`、`data`。

## PUT `/api/v1/devices/{id}`

修改设备 (UC18)

**权限**：hasAuthority('device:write')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer/int64 | 是 |  |

**JSON 请求体**：`Device`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | integer/int64 | 否 |  |
| `deviceCode` | string | 否 |  |
| `deviceName` | string | 否 |  |
| `deviceType` | string | 否 |  |
| `manufacturer` | string | 否 |  |
| `model` | string | 否 |  |
| `ipAddress` | string | 否 |  |
| `port` | integer/int32 | 否 |  |
| `location` | string | 否 |  |
| `locationDesc` | string | 否 |  |
| `waterAreaId` | integer/int64 | 否 |  |
| `status` | string | 否 |  |
| `lastHeartbeat` | string/date-time | 否 |  |
| `extraConfig` | object | 否 |  |
| `createdAt` | string/date-time | 否 |  |
| `updatedAt` | string/date-time | 否 |  |
| `deleted` | integer/int32 | 否 |  |
| `longitude` | number/double | 否 |  |
| `latitude` | number/double | 否 |  |
| `waterAreaName` | string | 否 |  |

**响应模型**：`ResultBoolean`；JSON 包含 `code`、`message`、`data`。

## POST `/api/v1/devices/{id}/confidence`

针对摄像头设备动态微调识别精度/灵敏度阈值 (滑轨控制/热重载)

**权限**：hasAuthority('device:control')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer/int64 | 是 |  |

**JSON 请求体**：`ConfidenceParam`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `confidence` | number/float | 否 |  |

**响应模型**：`ResultBoolean`；JSON 包含 `code`、`message`、`data`。

## POST `/api/v1/devices/{id}/model`

针对摄像头设备动态切换AI算法模型 (热重载)

**权限**：hasAuthority('device:control')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer/int64 | 是 |  |

**JSON 请求体**：`ModelParam`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `modelName` | string | 否 |  |

**响应模型**：`ResultBoolean`；JSON 包含 `code`、`message`、`data`。

## GET `/api/v1/drones/missions`

分页获取无人机任务记录

**权限**：hasAuthority('drone:read')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `pageNum` | query | integer/int32 | 否 | 1 |
| `pageSize` | query | integer/int32 | 否 | 10 |
| `status` | query | string | 否 |  |
| `droneId` | query | integer/int64 | 否 |  |

**响应模型**：`ResultPageDroneMission`；JSON 包含 `code`、`message`、`data`。

## POST `/api/v1/drones/missions/{id}/end`

指令无人机返航 (UC04)

**权限**：hasAuthority('drone:control')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer/int64 | 是 |  |

**响应模型**：`ResultBoolean`；JSON 包含 `code`、`message`、`data`。

## GET `/api/v1/drones/missions/{id}/telemetry`

查看遥测轨迹历史 (F-17)

**权限**：hasAuthority('drone:read')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer/int64 | 是 |  |

**响应模型**：`ResultListDroneFlightLog`；JSON 包含 `code`、`message`、`data`。

## POST `/api/v1/drones/plans`

制定巡航计划 (UC08)

**权限**：hasAuthority('drone:plan')

**JSON 请求体**：`DronePlan`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | integer/int64 | 否 |  |
| `planName` | string | 否 |  |
| `hangarId` | integer/int64 | 否 |  |
| `waterAreaId` | integer/int64 | 否 |  |
| `scheduleType` | string | 否 |  |
| `cronExpression` | string | 否 |  |
| `nextExecAt` | string/date-time | 否 |  |
| `flightAltitude` | integer/int32 | 否 |  |
| `flightSpeed` | number | 否 |  |
| `status` | string | 否 |  |
| `createdBy` | integer/int64 | 否 |  |
| `createdAt` | string/date-time | 否 |  |
| `updatedAt` | string/date-time | 否 |  |
| `deleted` | integer/int32 | 否 |  |
| `hangarName` | string | 否 |  |
| `waterAreaName` | string | 否 |  |
| `waypoints` | array<DroneWaypoint> | 否 |  |

**响应模型**：`ResultBoolean`；JSON 包含 `code`、`message`、`data`。

## GET `/api/v1/drones/plans/{id}`

查看巡航计划详情

**权限**：hasAuthority('drone:read')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer/int64 | 是 |  |

**响应模型**：`ResultDronePlan`；JSON 包含 `code`、`message`、`data`。

## POST `/api/v1/drones/takeoff`

调度无人机起飞 (UC04)

**权限**：hasAuthority('drone:control')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `alertEventId` | query | integer/int64 | 否 |  |
| `hangarId` | query | integer/int64 | 是 |  |

**响应模型**：`ResultDroneMission`；JSON 包含 `code`、`message`、`data`。

## POST `/api/v1/drones/telemetry`

上报实时遥测信息 (无人机端点)

**权限**：登录；未另设方法权限

**JSON 请求体**：`TelemetryParam`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `missionId` | integer/int64 | 否 |  |
| `longitude` | number/double | 否 |  |
| `latitude` | number/double | 否 |  |
| `altitude` | number | 否 |  |
| `speed` | number | 否 |  |
| `batteryPct` | integer/int32 | 否 |  |
| `signalDbm` | integer/int32 | 否 |  |

**响应模型**：`ResultBoolean`；JSON 包含 `code`、`message`、`data`。

## GET `/api/v1/statistics/overview`

全域安全态势指标总览 (今日实时大屏) (UC13)

**权限**：hasAuthority('stats:read')

**响应模型**：`ResultMapStringObject`；JSON 包含 `code`、`message`、`data`。

## GET `/api/v1/statistics/trends`

历史报警/处置趋势分析 (UC15)

**权限**：hasAuthority('stats:read')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `days` | query | integer/int32 | 否 | 7 |

**响应模型**：`ResultMapStringObject`；JSON 包含 `code`、`message`、`data`。

## GET `/api/v1/statistics/water-areas`

按水域统计报警频度分布

**权限**：hasAuthority('stats:read')

**响应模型**：`ResultMapStringObject`；JSON 包含 `code`、`message`、`data`。

## GET `/api/v1/tasks`

分页获取任务列表 (UC11)

**权限**：hasAuthority('task:read')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `pageNum` | query | integer/int32 | 否 | 1 |
| `pageSize` | query | integer/int32 | 否 | 10 |
| `status` | query | string | 否 |  |
| `assigneeId` | query | integer/int64 | 否 |  |
| `eventId` | query | integer/int64 | 否 |  |

**响应模型**：`ResultPageRescueTask`；JSON 包含 `code`、`message`、`data`。

## POST `/api/v1/tasks/dispatch`

手动指派/调度人员 (UC02 / UC09)

**权限**：hasAuthority('task:dispatch')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `eventId` | query | integer/int64 | 是 |  |

**响应模型**：`ResultRescueTask`；JSON 包含 `code`、`message`、`data`。

## POST `/api/v1/tasks/location`

上报实时定位 (UC12 / F-19)

**权限**：hasAuthority('task:update')

**JSON 请求体**：`LocationParam`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `longitude` | number/double | 否 |  |
| `latitude` | number/double | 否 |  |
| `accuracy` | number | 否 |  |

**响应模型**：`ResultBoolean`；JSON 包含 `code`、`message`、`data`。

## GET `/api/v1/tasks/{id}`

获取救援任务详情 (UC09)

**权限**：hasAuthority('task:read')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer/int64 | 是 |  |

**响应模型**：`ResultRescueTask`；JSON 包含 `code`、`message`、`data`。

## POST `/api/v1/tasks/{id}/accept`

接收/接受任务 (UC09)

**权限**：hasAuthority('task:update')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer/int64 | 是 |  |

**响应模型**：`ResultBoolean`；JSON 包含 `code`、`message`、`data`。

## POST `/api/v1/tasks/{id}/arrive`

到达现场上报 (UC10)

**权限**：hasAuthority('task:update')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer/int64 | 是 |  |

**JSON 请求体**：`ArriveParam`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `longitude` | number/double | 否 |  |
| `latitude` | number/double | 否 |  |

**响应模型**：`ResultBoolean`；JSON 包含 `code`、`message`、`data`。

## POST `/api/v1/tasks/{id}/complete`

完成救援任务 (UC10)

**权限**：hasAuthority('task:update')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer/int64 | 是 |  |

**JSON 请求体**：`CompleteParam`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `resultType` | string | 否 |  |
| `note` | string | 否 |  |
| `photoUrls` | array<string> | 否 |  |

**响应模型**：`ResultBoolean`；JSON 包含 `code`、`message`、`data`。

## POST `/api/v1/tasks/{id}/status`

标注更新任务状态 (已处理 / 识别错误)

**权限**：hasAuthority('task:update')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer/int64 | 是 |  |
| `status` | query | string | 是 |  |
| `note` | query | string | 否 |  |

**响应模型**：`ResultBoolean`；JSON 包含 `code`、`message`、`data`。

## GET `/api/v1/users`

分页获取用户列表

**权限**：hasAuthority('user:read')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `pageNum` | query | integer/int32 | 否 | 1 |
| `pageSize` | query | integer/int32 | 否 | 10 |
| `keyword` | query | string | 否 |  |

**响应模型**：`ResultPageSysUser`；JSON 包含 `code`、`message`、`data`。

## POST `/api/v1/users`

新增用户

**权限**：hasAuthority('user:write')

**JSON 请求体**：`SysUser`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | integer/int64 | 否 |  |
| `username` | string | 否 |  |
| `password` | string | 否 |  |
| `guardId` | string | 否 |  |
| `realName` | string | 否 |  |
| `phone` | string | 否 |  |
| `avatarUrl` | string | 否 |  |
| `status` | integer/int32 | 否 |  |
| `dutyStatus` | integer/int32 | 否 |  |
| `lastLoginAt` | string/date-time | 否 |  |
| `passwordReset` | boolean | 否 |  |
| `createdAt` | string/date-time | 否 |  |
| `updatedAt` | string/date-time | 否 |  |
| `deleted` | integer/int32 | 否 |  |
| `lastWktLocation` | string | 否 |  |
| `roleCode` | string | 否 |  |
| `deviceIds` | array<integer/int64> | 否 |  |

**响应模型**：`ResultSysUser`；JSON 包含 `code`、`message`、`data`。

## GET `/api/v1/users/active-locations`

获取当前在岗/活跃救生员最新位置列表

**权限**：登录；未另设方法权限

**响应模型**：`ResultListSysUser`；JSON 包含 `code`、`message`、`data`。

## PUT `/api/v1/users/duty-status`

救生员上报在岗状态 (UC12)

**权限**：hasAuthority('task:update')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `dutyStatus` | query | integer/int32 | 是 |  |

**响应模型**：`ResultBoolean`；JSON 包含 `code`、`message`、`data`。

## POST `/api/v1/users/notify`

预留救生员/人物通知推送接口 (预留接口)

**权限**：登录；未另设方法权限

**JSON 请求体**：`NotifyParam`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `userId` | integer/int64 | 否 |  |
| `message` | string | 否 |  |

**响应模型**：`ResultBoolean`；JSON 包含 `code`、`message`、`data`。

## DELETE `/api/v1/users/{id}`

逻辑删除用户

**权限**：hasAnyAuthority('user:delete', 'user:write', 'ROLE_ADMIN')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer/int64 | 是 |  |

**响应模型**：`ResultBoolean`；JSON 包含 `code`、`message`、`data`。

## PUT `/api/v1/users/{id}`

修改用户基本信息

**权限**：hasAuthority('user:write')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer/int64 | 是 |  |

**JSON 请求体**：`SysUser`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | integer/int64 | 否 |  |
| `username` | string | 否 |  |
| `password` | string | 否 |  |
| `guardId` | string | 否 |  |
| `realName` | string | 否 |  |
| `phone` | string | 否 |  |
| `avatarUrl` | string | 否 |  |
| `status` | integer/int32 | 否 |  |
| `dutyStatus` | integer/int32 | 否 |  |
| `lastLoginAt` | string/date-time | 否 |  |
| `passwordReset` | boolean | 否 |  |
| `createdAt` | string/date-time | 否 |  |
| `updatedAt` | string/date-time | 否 |  |
| `deleted` | integer/int32 | 否 |  |
| `lastWktLocation` | string | 否 |  |
| `roleCode` | string | 否 |  |
| `deviceIds` | array<integer/int64> | 否 |  |

**响应模型**：`ResultBoolean`；JSON 包含 `code`、`message`、`data`。

## POST `/api/v1/users/{id}/location`

后台模拟上报指定救生员的位置

**权限**：hasAuthority('user:write')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer/int64 | 是 |  |
| `lng` | query | number/double | 是 |  |
| `lat` | query | number/double | 是 |  |
| `dutyStatus` | query | integer/int32 | 否 |  |

**响应模型**：`ResultBoolean`；JSON 包含 `code`、`message`、`data`。

## GET `/api/v1/water-areas`

分页查询水域列表

**权限**：hasAuthority('waterarea:read')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `pageNum` | query | integer/int32 | 否 | 1 |
| `pageSize` | query | integer/int32 | 否 | 10 |
| `keyword` | query | string | 否 |  |

**响应模型**：`ResultPageWaterArea`；JSON 包含 `code`、`message`、`data`。

## POST `/api/v1/water-areas`

创建新水域

**权限**：hasAuthority('waterarea:write')

**JSON 请求体**：`WaterArea`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | integer/int64 | 否 |  |
| `name` | string | 否 |  |
| `areaCode` | string | 否 |  |
| `level` | integer/int32 | 否 |  |
| `boundary` | string | 否 |  |
| `centerPoint` | string | 否 |  |
| `description` | string | 否 |  |
| `createdAt` | string/date-time | 否 |  |
| `updatedAt` | string/date-time | 否 |  |
| `deleted` | integer/int32 | 否 |  |
| `centerLng` | number/double | 否 |  |
| `centerLat` | number/double | 否 |  |

**响应模型**：`ResultBoolean`；JSON 包含 `code`、`message`、`data`。

## GET `/api/v1/water-areas/containing`

根据经纬度反查所在水域

**权限**：hasAuthority('waterarea:read')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `lng` | query | number/double | 是 |  |
| `lat` | query | number/double | 是 |  |

**响应模型**：`ResultListWaterArea`；JSON 包含 `code`、`message`、`data`。

## DELETE `/api/v1/water-areas/{id}`

逻辑删除水域

**权限**：hasAuthority('waterarea:write')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer/int64 | 是 |  |

**响应模型**：`ResultBoolean`；JSON 包含 `code`、`message`、`data`。

## GET `/api/v1/water-areas/{id}`

根据ID获取水域详情

**权限**：hasAuthority('waterarea:read')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer/int64 | 是 |  |

**响应模型**：`ResultWaterArea`；JSON 包含 `code`、`message`、`data`。

## PUT `/api/v1/water-areas/{id}`

修改水域

**权限**：hasAuthority('waterarea:write')

| 参数 | 位置 | 类型 | 必填 | 默认/说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer/int64 | 是 |  |

**JSON 请求体**：`WaterArea`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | integer/int64 | 否 |  |
| `name` | string | 否 |  |
| `areaCode` | string | 否 |  |
| `level` | integer/int32 | 否 |  |
| `boundary` | string | 否 |  |
| `centerPoint` | string | 否 |  |
| `description` | string | 否 |  |
| `createdAt` | string/date-time | 否 |  |
| `updatedAt` | string/date-time | 否 |  |
| `deleted` | integer/int32 | 否 |  |
| `centerLng` | number/double | 否 |  |
| `centerLat` | number/double | 否 |  |

**响应模型**：`ResultBoolean`；JSON 包含 `code`、`message`、`data`。

