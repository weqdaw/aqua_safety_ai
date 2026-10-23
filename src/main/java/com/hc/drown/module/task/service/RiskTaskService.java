package com.hc.drown.module.task.service;

import com.hc.drown.module.device.entity.Device;
import com.hc.drown.module.device.service.DeviceService;
import com.hc.drown.module.device.service.StreamDistributionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskTaskService {
    private final JdbcTemplate jdbc;
    private final StreamDistributionService distribution;
    private final DeviceService devices;
    private final TransactionTemplate transactions;
    private static final Set<String> STATUSES = Set.of("PENDING", "PROCESSED", "RECOGNITION_ERROR");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String TASK_COLUMNS = """
        id, incident_key AS "incidentKey", stream_id AS "streamId", event_name AS "eventName", details,
        confidence, to_char(occurred_at, 'YYYY-MM-DD HH24:MI:SS') AS "occurredAt",
        source_device_id AS "sourceDeviceId", source_device_name AS "sourceDeviceName",
        source_device_code AS "sourceDeviceCode", source_device_type AS "sourceDeviceType",
        device_ip AS "deviceIp", selected_model AS "selectedModel", location, location_desc AS "locationDesc",
        snapshot_url AS "snapshotUrl", video_url AS "videoUrl", media_status AS "mediaStatus",
        media_error AS "mediaError", status, handled_by AS "handledBy",
        to_char(handled_at, 'YYYY-MM-DD HH24:MI:SS') AS "handledAt", note
        """;

    @Scheduled(fixedDelay = 3000)
    public void synchronizeIncidents() {
        try {
            Long revision = jdbc.queryForObject("SELECT COALESCE(MAX(source_revision), 0) FROM risk_detection_task", Long.class);
            List<Map<String, Object>> incoming = distribution.getRiskIncidents(revision == null ? 0 : revision, 100);
            if (incoming == null || incoming.isEmpty()) return;
            List<Device> cameras = devices.getDevicesByType("CAMERA");
            // A batch either imports completely or retries. Media updates never change human annotations.
            transactions.executeWithoutResult(tx -> incoming.forEach(item -> importIncident(item, cameras)));
        } catch (Exception e) {
            log.warn("风险任务同步暂未完成，将自动重试: {}", e.getMessage());
        }
    }

    void importIncident(Map<String, Object> item, List<Device> cameras) {
        String key = Objects.toString(item.get("incident_key"), "");
        if (!key.matches("[a-f0-9]{32}")) throw new IllegalArgumentException("Invalid risk incident key");
        String stream = Objects.toString(item.get("stream_id"), "");
        Device device = cameras.stream().filter(d -> stream.equalsIgnoreCase(d.getDeviceCode())).findFirst().orElse(null);
        String snapshot = mediaPath(item.get("snapshot_url"), key, "jpg");
        String video = mediaPath(item.get("video_url"), key, "mp4");
        Object model = device == null || device.getExtraConfig() == null ? null : device.getExtraConfig().get("selectedModel");
        List<Long> imported = jdbc.queryForList("""
            INSERT INTO risk_detection_task (incident_key, source_revision, stream_id, event_name, details, confidence,
                occurred_at, source_device_id, source_device_name, source_device_code, source_device_type, device_ip,
                selected_model, location, location_desc, snapshot_url, video_url, media_status, media_error)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (incident_key) DO UPDATE SET
                source_revision = EXCLUDED.source_revision,
                snapshot_url = COALESCE(risk_detection_task.snapshot_url, EXCLUDED.snapshot_url),
                video_url = EXCLUDED.video_url, media_status = EXCLUDED.media_status, media_error = EXCLUDED.media_error
            WHERE EXCLUDED.source_revision > risk_detection_task.source_revision
            RETURNING id
            """, Long.class, key, ((Number)item.get("revision")).longValue(), stream,
            item.get("event_name"), item.get("details"), item.get("confidence"),
            LocalDateTime.parse(item.get("created_at").toString(), TIME), device == null ? null : device.getId(),
            device == null ? stream : device.getDeviceName(), device == null ? stream : device.getDeviceCode(),
            device == null ? "CAMERA" : device.getDeviceType(), device == null ? null : device.getIpAddress(),
            model == null ? null : model.toString(), device == null ? null : device.getLocation(),
            device == null ? null : device.getLocationDesc(), snapshot, video, item.get("media_status"), item.get("media_error"));
        if (imported.isEmpty()) return; // The same or an older source revision is an idempotent no-op.
        Long id = imported.get(0);
        jdbc.update("""
            INSERT INTO risk_detection_task_history(task_id, from_status, to_status, operator_name, note)
            SELECT ?, NULL, 'PENDING', 'AI 风险检测', '安全动态监测触发红标告警，自动创建任务'
            WHERE NOT EXISTS (SELECT 1 FROM risk_detection_task_history WHERE task_id = ?)
            """, id, id);
    }

    private String mediaPath(Object value, String key, String suffix) {
        String expected = "/static/risk-evidence/" + key + "." + suffix;
        return expected.equals(value) ? expected : null;
    }

    public Map<String, Object> list(int pageNum, int pageSize, String status) {
        return list(pageNum, pageSize, status, null, null, null, null);
    }

    public Map<String, Object> list(int pageNum, int pageSize, String status, String deviceCode,
                                     String keyword, String startTime, String endTime) {
        if (status != null && !STATUSES.contains(status)) throw new RiskTaskException(HttpStatus.BAD_REQUEST, "任务状态无效");
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) throw new RiskTaskException(HttpStatus.BAD_REQUEST, "pageNum需大于0，pageSize范围为1至100");
        if (keyword != null && keyword.length() > 200) throw new RiskTaskException(HttpStatus.BAD_REQUEST, "关键词不能超过200字");
        LocalDateTime start = parseTime(startTime), end = parseTime(endTime);
        if (start != null && end != null && start.isAfter(end)) throw new RiskTaskException(HttpStatus.BAD_REQUEST, "开始时间不能晚于结束时间");
        int page = Math.max(1, pageNum), size = Math.max(1, Math.min(100, pageSize));
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (status != null) { conditions.add("status = ?"); params.add(status); }
        if (deviceCode != null && !deviceCode.isBlank()) {
            conditions.add("LOWER(source_device_code) = LOWER(?)"); params.add(deviceCode.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            conditions.add("(event_name ILIKE ? OR source_device_name ILIKE ? OR location_desc ILIKE ?)");
            String pattern = "%" + keyword.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            params.addAll(List.of(pattern, pattern, pattern));
        }
        if (start != null) { conditions.add("occurred_at >= ?"); params.add(start); }
        if (end != null) { conditions.add("occurred_at <= ?"); params.add(end); }
        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM risk_detection_task" + where, Long.class, params.toArray());
        params.add(size);
        params.add((long)(page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT " + TASK_COLUMNS + " FROM risk_detection_task" + where
            + " ORDER BY occurred_at DESC, id DESC LIMIT ? OFFSET ?", params.toArray());
        return Map.of("records", rows, "total", total == null ? 0L : total, "counts", counts(), "current", page, "size", size);
    }

    public Map<String, Long> counts() {
        Map<String, Long> counts = new HashMap<>(Map.of("total", 0L, "pending", 0L, "processed", 0L, "error", 0L));
        for (Map<String, Object> row : jdbc.queryForList("SELECT status, COUNT(*) AS n FROM risk_detection_task GROUP BY status")) {
            long n = ((Number)row.get("n")).longValue();
            String name = switch (row.get("status").toString()) {
                case "PENDING" -> "pending";
                case "PROCESSED" -> "processed";
                default -> "error";
            };
            counts.put(name, n);
            counts.put("total", counts.get("total") + n);
        }
        return counts;
    }

    private LocalDateTime parseTime(String value) {
        if (value == null) return null;
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
                .withResolverStyle(java.time.format.ResolverStyle.STRICT));
        } catch (java.time.format.DateTimeParseException e) {
            throw new RiskTaskException(HttpStatus.BAD_REQUEST, "时间格式应为yyyy-MM-dd HH:mm:ss");
        }
    }

    public Map<String, Object> get(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT " + TASK_COLUMNS + " FROM risk_detection_task WHERE id = ?", id);
        if (rows.isEmpty()) throw new RiskTaskException(HttpStatus.NOT_FOUND, "风险任务不存在");
        return rows.get(0);
    }

    @Transactional
    public boolean annotate(long id, String status, String expectedStatus, String note, Long userId, String username) {
        if (status == null || !Set.of("PROCESSED", "RECOGNITION_ERROR").contains(status)) throw new RiskTaskException(HttpStatus.BAD_REQUEST, "请选择已处理或识别错误");
        if (note != null && note.length() > 1000) throw new RiskTaskException(HttpStatus.BAD_REQUEST, "处理备注不能超过1000字");
        List<String> existing = jdbc.queryForList("SELECT status FROM risk_detection_task WHERE id = ? FOR UPDATE", String.class, id);
        if (existing.isEmpty()) throw new RiskTaskException(HttpStatus.NOT_FOUND, "风险任务不存在");
        String previous = existing.get(0);
        if (previous.equals(status)) return true;
        if (!previous.equals(expectedStatus)) throw new RiskTaskException(HttpStatus.CONFLICT, "任务已被其他用户更新，请刷新后重试");
        jdbc.update("UPDATE risk_detection_task SET status = ?, handled_by = ?, handled_at = NOW(), note = ? WHERE id = ?",
            status, username, note, id);
        jdbc.update("""
            INSERT INTO risk_detection_task_history(task_id, from_status, to_status, operator_id, operator_name, note)
            VALUES (?, ?, ?, ?, ?, ?)
            """, id, previous, status, userId, username, note);
        return true;
    }

    public List<Map<String, Object>> history(long id) {
        get(id);
        return jdbc.queryForList("""
            SELECT id, from_status AS "fromStatus", to_status AS "toStatus", operator_name AS "operatorName", note,
                to_char(created_at, 'YYYY-MM-DD HH24:MI:SS') AS "createdAt"
            FROM risk_detection_task_history WHERE task_id = ? ORDER BY id
            """, id);
    }
}
