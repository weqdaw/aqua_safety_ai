package com.hc.drown.module.task.controller;

import com.hc.drown.common.api.Result;
import com.hc.drown.module.task.service.RiskTaskService;
import com.hc.drown.module.task.service.RiskTaskMediaService;
import com.hc.drown.module.task.service.RiskTaskException;
import com.hc.drown.module.user.dto.AdminUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@Tag(name = "AI风险任务（移动端与大屏）", description = "完整的风险任务查询、核验、历史及8080证据访问接口")
@RestController
@RequestMapping({"/api/v1/risk-tasks", "/api/v1/mobile/risk-tasks"})
@RequiredArgsConstructor
public class RiskTaskController {
    private final RiskTaskService service;
    private final RiskTaskMediaService media;

    @Operation(summary = "分页查询风险任务", description = "支持状态、设备编码、关键词和告警时间筛选；counts为全量统计")
    @GetMapping
    @PreAuthorize("hasAuthority('task:read')")
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "12") int pageSize,
            @Parameter(schema = @Schema(allowableValues = {"PENDING", "PROCESSED", "RECOGNITION_ERROR"})) @RequestParam(required = false) String status,
            @RequestParam(required = false) String deviceCode, @RequestParam(required = false) String keyword,
            @Parameter(description = "北京时间，yyyy-MM-dd HH:mm:ss（含起点）") @RequestParam(required = false) String startTime,
            @Parameter(description = "北京时间，yyyy-MM-dd HH:mm:ss（含终点）") @RequestParam(required = false) String endTime,
            HttpServletRequest request) {
        Map<String, Object> result = new LinkedHashMap<>(service.list(pageNum, pageSize, status, deviceCode, keyword, startTime, endTime));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>)result.get("records");
        result.put("records", records.stream().map(task -> view(task, request)).toList());
        return Result.success(result);
    }

    @Operation(summary = "获取风险任务全量状态统计")
    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('task:read')")
    public Result<Map<String, Long>> stats() { return Result.success(service.counts()); }

    @Operation(summary = "获取单个风险任务详情（含处理历史）")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('task:read')")
    public Result<Map<String, Object>> detail(@PathVariable long id, HttpServletRequest request) {
        Map<String, Object> task = view(service.get(id), request);
        task.put("history", service.history(id));
        return Result.success(task);
    }

    @Operation(summary = "获取任务完整处理历史")
    @GetMapping("/{id}/history")
    @PreAuthorize("hasAuthority('task:read')")
    public Result<List<Map<String, Object>>> history(@PathVariable long id) {
        return Result.success(service.history(id));
    }

    @PostMapping("/{id}/status")
    @Operation(summary = "标注已处理或识别错误", description = "expectedStatus传入页面读取的原状态；并发状态冲突返回HTTP 409")
    @PreAuthorize("hasAnyAuthority('task:update', 'alert:confirm')")
    public Result<Boolean> annotate(@PathVariable long id, @Valid @RequestBody Annotation body) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof AdminUserDetails user)) return Result.unauthorized(null);
        String name = user.getSysUser().getRealName();
        return Result.success(service.annotate(id, body.getStatus(), body.getExpectedStatus(), body.getNote(),
            user.getSysUser().getId(), name == null || name.isBlank() ? user.getUsername() : name));
    }

    @Operation(summary = "获取5分钟有效的截图/视频链接", description = "先使用Bearer令牌获取链接，再交给不支持自定义请求头的移动端媒体组件")
    @GetMapping("/{id}/media-links")
    @PreAuthorize("hasAuthority('task:read')")
    public Result<Map<String, Object>> mediaLinks(@PathVariable long id) { return Result.success(media.links(id)); }

    @Operation(summary = "读取告警带框截图", description = "Bearer鉴权或media-links中的access；返回JPEG二进制，可使用download=true下载")
    @GetMapping("/{id}/snapshot")
    public ResponseEntity<StreamingResponseBody> snapshot(@PathVariable long id,
            @RequestParam(required = false) String access, @RequestParam(defaultValue = "false") boolean download,
            @RequestHeader(value = "Range", required = false) String range, HttpServletRequest request) {
        authorizeMedia(id, "snapshot", access);
        return media.stream(id, "snapshot", range, download, "HEAD".equals(request.getMethod()));
    }

    @Operation(summary = "播放或下载告警视频", description = "返回H.264 MP4二进制，支持单段Range；录制中返回409，归档后可回放")
    @GetMapping("/{id}/video")
    public ResponseEntity<StreamingResponseBody> video(@PathVariable long id,
            @RequestParam(required = false) String access, @RequestParam(defaultValue = "false") boolean download,
            @RequestHeader(value = "Range", required = false) String range, HttpServletRequest request) {
        authorizeMedia(id, "video", access);
        return media.stream(id, "video", range, download, "HEAD".equals(request.getMethod()));
    }

    private void authorizeMedia(long id, String kind, String access) {
        if (media.validAccess(id, kind, access)) return;
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated())
            throw new RiskTaskException(HttpStatus.UNAUTHORIZED, "请登录，或使用有效的短时媒体链接");
        if (auth.getAuthorities().stream().noneMatch(a -> "task:read".equals(a.getAuthority())))
            throw new RiskTaskException(HttpStatus.FORBIDDEN, "没有查看风险任务的权限");
    }

    private Map<String, Object> view(Map<String, Object> original, HttpServletRequest request) {
        Map<String, Object> task = new LinkedHashMap<>(original);
        String prefix = "/api/v1/mobile/risk-tasks/" + task.get("id");
        task.put("snapshotApiUrl", task.get("snapshotUrl") == null ? null : prefix + "/snapshot");
        task.put("videoApiUrl", task.get("videoUrl") == null ? null : prefix + "/video");
        task.put("mediaLinksUrl", prefix + "/media-links");
        if (request.getRequestURI().startsWith("/api/v1/mobile/")) {
            task.put("snapshotUrl", task.get("snapshotApiUrl"));
            task.put("videoUrl", task.get("videoApiUrl"));
        }
        return task;
    }

    @Data
    public static class Annotation {
        @NotBlank
        @Pattern(regexp = "PROCESSED|RECOGNITION_ERROR")
        @Schema(description = "目标状态", allowableValues = {"PROCESSED", "RECOGNITION_ERROR"}, example = "PROCESSED")
        private String status;
        @NotBlank
        @Pattern(regexp = "PENDING|PROCESSED|RECOGNITION_ERROR")
        @Schema(description = "上次读取的任务原状态", example = "PENDING")
        private String expectedStatus;
        @Size(max = 1000)
        @Schema(description = "处理备注，最多1000字", example = "已现场核验，人员安全")
        private String note;
    }
}
