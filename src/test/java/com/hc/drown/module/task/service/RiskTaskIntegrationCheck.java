package com.hc.drown.module.task.service;

import com.hc.drown.common.exception.BusinessException;
import com.hc.drown.module.device.entity.Device;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import com.hc.drown.config.JwtProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.io.ByteArrayOutputStream;
import org.springframework.http.HttpStatus;

/** Runs against an isolated PostgreSQL schema; the entire check is rolled back. */
public class RiskTaskIntegrationCheck {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            System.getenv("RISK_TEST_DATABASE_URL"), System.getenv("RISK_TEST_DATABASE_USERNAME"),
            System.getenv("RISK_TEST_DATABASE_PASSWORD"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        String migration = Files.readString(Path.of(args[0]));
        tx.executeWithoutResult(state -> {
            String schema = "risk_check_" + UUID.randomUUID().toString().replace("-", "");
            jdbc.execute("CREATE SCHEMA " + schema);
            jdbc.execute("SET LOCAL search_path TO " + schema);
            jdbc.execute(migration);
            RiskTaskService service = new RiskTaskService(jdbc, null, null, tx);
            Device device = new Device();
            device.setId(17L);
            device.setDeviceCode("camera-a");
            device.setDeviceName("测试监控点");
            device.setDeviceType("CAMERA");
            device.setIpAddress("192.0.2.1");
            device.setLocation("POINT(120.1 28.2)");
            device.setLocationDesc("测试水域");
            device.setExtraConfig(new HashMap<>(Map.of("selectedModel", "check1.pt")));
            String key = "1234567890abcdef1234567890abcdef";
            Map<String, Object> source = new HashMap<>(Map.of(
                "incident_key", key, "revision", 1L, "stream_id", "camera-a", "event_name", "红标告警",
                "details", "真实检测事件", "confidence", .87, "created_at", "2026-10-20 15:00:00",
                "snapshot_url", "/static/risk-evidence/" + key + ".jpg", "media_status", "RECORDING"));
            service.importIncident(source, List.of(device));
            service.importIncident(source, List.of(device));
            long id = jdbc.queryForObject("SELECT id FROM risk_detection_task", Long.class);
            check(jdbc.queryForObject("SELECT COUNT(*) FROM risk_detection_task", Integer.class) == 1, "Repeated polling duplicated a task");
            check("PENDING".equals(jdbc.queryForObject("SELECT status FROM risk_detection_task", String.class)), "Initial state was not pending");
            check(service.history(id).size() == 1, "Creation history duplicated");
            service.annotate(id, "PROCESSED", "PENDING", "已现场核验", 1L, "值守员");
            source.put("revision", 2L);
            source.put("media_status", "READY");
            source.put("video_url", "/static/risk-evidence/" + key + ".mp4");
            device.setDeviceName("后续更名");
            service.importIncident(source, List.of(device));
            check("PROCESSED".equals(jdbc.queryForObject("SELECT status FROM risk_detection_task", String.class)), "Media sync overwrote annotation");
            check("测试监控点".equals(jdbc.queryForObject("SELECT source_device_name FROM risk_detection_task", String.class)), "Historical device metadata was overwritten");
            check(service.history(id).size() == 2, "Source update created processing history");
            try {
                service.annotate(id, "RECOGNITION_ERROR", "PENDING", "过期操作", 2L, "其他用户");
                throw new AssertionError("Stale update was accepted");
            } catch (BusinessException expected) { /* concurrent update correctly rejected */ }
            try {
                service.annotate(id, "INVALID", "PROCESSED", null, 1L, "值守员");
                throw new AssertionError("Invalid status was accepted");
            } catch (BusinessException expected) { /* validation */ }
            service.annotate(id, "RECOGNITION_ERROR", "PROCESSED", "反光导致误识别 ' test", 1L, "值守员");
            check(service.history(id).size() == 3, "Annotation history was not retained");
            Map<String, Object> page = service.list(1, 12, "RECOGNITION_ERROR");
            check(((Number)page.get("total")).intValue() == 1, "Filtered total incorrect");
            check(((Map<?, ?>)page.get("counts")).get("error").equals(1L), "Global status counts incorrect");
            check(((List<?>)service.list(2, 1, null).get("records")).isEmpty(), "Pagination failed");
            RiskTaskService afterRestart = new RiskTaskService(jdbc, null, null, tx);
            check(afterRestart.history(id).size() == 3, "History depends on service memory");
            check(((Number)((Map<?, ?>)((List<?>)afterRestart.list(1, 12, null).get("records")).get(0)).get("confidence")).doubleValue() == .87, "Confidence changed");
            check(service.get(id).get("sourceDeviceCode").equals("camera-a"), "Task detail missing device information");
            check(((Number)service.list(1, 12, null, "CAMERA-A", "测试水域", "2026-10-20 14:00:00", "2026-10-20 16:00:00").get("total")).intValue() == 1, "Combined mobile filters failed");
            check(((Number)service.list(1, 12, null, "missing-device", null, null, null).get("total")).intValue() == 0, "Device filter ignored");
            check(((Number)service.list(1, 12, null, null, "测试_", null, null).get("total")).intValue() == 0, "Keyword wildcard was not escaped");
            check(((Number)service.list(1, 12, null, null, null, "2026-10-21 00:00:00", null).get("total")).intValue() == 0, "Time filter ignored");
            expectFailure(() -> service.get(-1), HttpStatus.NOT_FOUND);
            expectFailure(() -> service.history(-1), HttpStatus.NOT_FOUND);
            expectFailure(() -> service.list(1, 101, null), HttpStatus.BAD_REQUEST);
            expectFailure(() -> service.list(1, 12, null, null, null, "2026-02-30 00:00:00", null), HttpStatus.BAD_REQUEST);
            expectFailure(() -> service.list(1, 12, null, null, null, "2026-10-21 00:00:00", "2026-10-20 00:00:00"), HttpStatus.BAD_REQUEST);
            checkMedia(service, jdbc, id);
            state.setRollbackOnly();
        });
        System.out.println("PASS: source deduplication, persistence, annotations, detail, mobile filters, time validation, signed media, streaming, Range, HEAD and downloads");
    }

    private static void expectFailure(Runnable action, HttpStatus status) {
        try { action.run(); throw new AssertionError("Expected HTTP " + status.value()); }
        catch (RiskTaskException e) { check(e.getHttpStatus() == status, "Incorrect API error status"); }
    }

    private static void checkMedia(RiskTaskService tasks, JdbcTemplate jdbc, long id) {
        HttpServer server = null;
        try {
            byte[] payload = "0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/static/risk-evidence/", exchange -> {
                String range = exchange.getRequestHeaders().getFirst("Range");
                exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
                if ("HEAD".equals(exchange.getRequestMethod())) {
                    exchange.getResponseHeaders().set("Content-Length", String.valueOf(payload.length));
                    exchange.sendResponseHeaders(200, -1);
                } else if ("bytes=0-3".equals(range)) {
                    exchange.getResponseHeaders().set("Content-Range", "bytes 0-3/16");
                    exchange.sendResponseHeaders(206, 4);
                    exchange.getResponseBody().write(Arrays.copyOf(payload, 4));
                } else if ("bytes=100-200".equals(range)) {
                    exchange.getResponseHeaders().set("Content-Range", "bytes */16");
                    exchange.sendResponseHeaders(416, -1);
                } else {
                    exchange.sendResponseHeaders(200, payload.length);
                    exchange.getResponseBody().write(payload);
                }
                exchange.close();
            });
            server.start();
            JwtProperties jwt = new JwtProperties();
            jwt.setSecret("isolated-media-check-secret-at-least-32-bytes");
            RiskTaskMediaService media = new RiskTaskMediaService(tasks, jwt, "http://127.0.0.1:" + server.getAddress().getPort());
            Map<String, Object> links = media.links(id);
            String access = links.get("videoUrl").toString().split("access=", 2)[1];
            check(media.validAccess(id, "video", access), "Signed media URL failed");
            check(!media.validAccess(id + 1, "video", access), "Signature was not bound to task");
            check(!media.validAccess(id, "snapshot", access), "Signature was not bound to media kind");
            check(!media.validAccess(id, "video", "1.invalid"), "Expired link accepted");
            check(!media.validAccess(id, "video", access + "invalid"), "Tampered link accepted");
            var response = media.stream(id, "video", "bytes=0-3", true);
            check(response.getStatusCode().value() == 206, "Range status was not preserved");
            check("bytes 0-3/16".equals(response.getHeaders().getFirst("Content-Range")), "Content-Range missing");
            check(response.getHeaders().getContentDisposition().isAttachment(), "Download disposition missing");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            response.getBody().writeTo(output);
            check(Arrays.equals(output.toByteArray(), Arrays.copyOf(payload, 4)), "Proxy corrupted media bytes");
            var head = media.stream(id, "snapshot", null, false, true);
            check(head.getBody() == null && head.getHeaders().getContentLength() == 16, "HEAD did not preserve metadata");
            var unsatisfied = media.stream(id, "video", "bytes=100-200", false);
            check(unsatisfied.getStatusCode().value() == 416, "Unsatisfied range was not preserved");
            check("bytes */16".equals(unsatisfied.getHeaders().getFirst("Content-Range")), "Range error metadata missing");
            expectFailure(() -> media.stream(id, "video", "bytes=0-1,2-3", false), HttpStatus.BAD_REQUEST);
            jdbc.update("UPDATE risk_detection_task SET media_status = 'RECORDING' WHERE id = ?", id);
            expectFailure(() -> media.stream(id, "video", null, false), HttpStatus.CONFLICT);
            jdbc.update("UPDATE risk_detection_task SET media_status = 'READY' WHERE id = ?", id);
        } catch (Exception e) { throw new RuntimeException(e); }
        finally { if (server != null) server.stop(0); }
    }
}
