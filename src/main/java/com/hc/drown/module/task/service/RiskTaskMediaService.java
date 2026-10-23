package com.hc.drown.module.task.service;

import com.hc.drown.config.JwtProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class RiskTaskMediaService {
    private final RiskTaskService tasks;
    private final JwtProperties jwt;
    private final String distributionUrl;
    private static final long LINK_TTL = 300;

    public RiskTaskMediaService(RiskTaskService tasks, JwtProperties jwt,
            @Value("${hc.drown.distribution.url:http://127.0.0.1:8000}") String distributionUrl) {
        this.tasks = tasks;
        this.jwt = jwt;
        this.distributionUrl = distributionUrl.replaceAll("/+$", "");
    }

    public Map<String, Object> links(long id) {
        Map<String, Object> task = tasks.get(id);
        long expiry = Instant.now().getEpochSecond() + LINK_TTL;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("expiresAt", expiry);
        result.put("expiresIn", LINK_TTL);
        result.put("mediaStatus", task.get("mediaStatus"));
        result.put("snapshotUrl", task.get("snapshotUrl") == null ? null : link(id, "snapshot", expiry));
        result.put("videoUrl", task.get("videoUrl") == null || !"READY".equals(task.get("mediaStatus")) ? null : link(id, "video", expiry));
        return result;
    }

    private String link(long id, String kind, long expiry) {
        return "/api/v1/mobile/risk-tasks/" + id + "/" + kind + "?access=" + expiry + "." + sign(id, kind, expiry);
    }

    private String sign(long id, String kind, long expiry) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwt.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String payload = "risk-task-media\n" + id + "\n" + kind + "\n" + expiry;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Media signing configuration unavailable", e);
        }
    }

    public boolean validAccess(long id, String kind, String access) {
        if (access == null || access.length() > 100) return false;
        String[] parts = access.split("\\.", -1);
        if (parts.length != 2) return false;
        try {
            long expiry = Long.parseLong(parts[0]), now = Instant.now().getEpochSecond();
            return expiry > now && expiry <= now + LINK_TTL
                && MessageDigest.isEqual(sign(id, kind, expiry).getBytes(StandardCharsets.US_ASCII), parts[1].getBytes(StandardCharsets.US_ASCII));
        } catch (NumberFormatException e) { return false; }
    }

    public ResponseEntity<StreamingResponseBody> stream(long id, String kind, String range, boolean download) {
        return stream(id, kind, range, download, false);
    }

    public ResponseEntity<StreamingResponseBody> stream(long id, String kind, String range, boolean download, boolean head) {
        Map<String, Object> task = tasks.get(id);
        boolean video = "video".equals(kind);
        if (video && "RECORDING".equals(task.get("mediaStatus")))
            throw new RiskTaskException(HttpStatus.CONFLICT, "告警视频仍在录制，请稍后重试");
        Object path = task.get(video ? "videoUrl" : "snapshotUrl");
        String expected = "/static/risk-evidence/" + task.get("incidentKey") + (video ? ".mp4" : ".jpg");
        if (!expected.equals(path)) throw new RiskTaskException(HttpStatus.NOT_FOUND, "任务媒体尚不可用");
        if (range != null && !range.matches("bytes=(?:[0-9]+-[0-9]*|-[0-9]+)"))
            throw new RiskTaskException(HttpStatus.BAD_REQUEST, "仅支持单段Range，例如bytes=0-1023");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection)URI.create(distributionUrl + expected).toURL().openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(false);
            if (head) connection.setRequestMethod("HEAD");
            if (range != null) connection.setRequestProperty("Range", range);
            int status = connection.getResponseCode();
            HttpHeaders headers = new HttpHeaders();
            headers.setCacheControl("private, no-store");
            for (String name : List.of("Content-Length", "Content-Range", "Accept-Ranges")) {
                String value = connection.getHeaderField(name);
                if (value != null) headers.set(name, value);
            }
            if (status == 416) {
                headers.remove(HttpHeaders.CONTENT_LENGTH);
                connection.disconnect();
                return new ResponseEntity<>(null, headers, HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
            }
            if (status != 200 && status != 206) {
                connection.disconnect();
                throw new RiskTaskException(status == 404 ? HttpStatus.NOT_FOUND : HttpStatus.BAD_GATEWAY,
                    status == 404 ? "媒体文件不存在" : "证据服务暂不可用");
            }
            headers.setContentType(video ? MediaType.valueOf("video/mp4") : MediaType.IMAGE_JPEG);
            headers.setContentDisposition((download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename("risk-task-" + id + (video ? ".mp4" : ".jpg")).build());
            if (head) {
                connection.disconnect();
                return new ResponseEntity<>(null, headers, HttpStatusCode.valueOf(status));
            }
            HttpURLConnection upstream = connection;
            StreamingResponseBody body = output -> {
                try (var input = upstream.getInputStream()) { input.transferTo(output); }
                finally { upstream.disconnect(); }
            };
            return new ResponseEntity<>(body, headers, HttpStatusCode.valueOf(status));
        } catch (IOException e) {
            if (connection != null) connection.disconnect();
            throw new RiskTaskException(HttpStatus.BAD_GATEWAY, "证据服务连接失败，请稍后重试");
        }
    }
}
