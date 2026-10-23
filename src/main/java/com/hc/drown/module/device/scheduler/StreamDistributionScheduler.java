package com.hc.drown.module.device.scheduler;

import com.hc.drown.module.device.entity.Device;
import com.hc.drown.module.device.service.DeviceService;
import com.hc.drown.module.device.service.StreamDistributionService;
import com.hc.drown.module.device.service.StreamDistributionService.StreamData;
import com.hc.drown.module.device.service.StreamDistributionService.StreamInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 监控分发系统定时同步调度器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StreamDistributionScheduler {

    private final StreamDistributionService streamDistributionService;
    private final DeviceService deviceService;
    

    /**
     * 每3秒同步一次实时监控流的AI推理结果与状态
     */
    @Scheduled(fixedDelay = 3000)
    public void syncDistributionData() {
        // 1. 获取视频处理平台中的所有监控流通道列表
        List<StreamInfo> streamInfoList = streamDistributionService.getStreams();
        if (streamInfoList == null) {
            // 断连时保留设备数据，不生成仿真检测或任务。
            return;
        }

        // 2. 获取实时检测数据
        List<StreamData> streamDataList = streamDistributionService.getRealtimeData();
        Map<String, StreamData> streamDataMap = new HashMap<>();
        if (streamDataList != null) {
            for (StreamData data : streamDataList) {
                if (data.getStreamId() != null) {
                    streamDataMap.put(data.getStreamId().toLowerCase(), data);
                }
            }
        }

        // 3. 获取大屏端现有的所有未删除的摄像头
        List<Device> cameras = deviceService.getDevicesByType("CAMERA");

        // 4. 同步大屏端摄像头与视频平台的直播流
        Set<String> activeStreamIds = new HashSet<>();
        for (StreamInfo stream : streamInfoList) {
            if (stream.getId() == null) continue;
            String streamId = stream.getId();
            activeStreamIds.add(streamId.toLowerCase());

            // 检查数据库中是否存在该摄像头 (不论是否deleted)
            Device device = deviceService.getDeviceByCodeIncludeDeleted(streamId);

            boolean isRunning = "Running".equalsIgnoreCase(stream.getStatus());
            String newStatus = isRunning ? "ONLINE" : "OFFLINE";

            if (device != null) {
                // 如果是已删除的，恢复它
                if (device.getDeleted() != null && device.getDeleted() == 1) {
                    device.setDeleted(0);
                    log.info("从视频分发系统重新发现通道，恢复大屏摄像头设备: {}", streamId);
                }
                device.setDeviceName(stream.getName());
                device.setStatus(newStatus);
                device.setLastHeartbeat(LocalDateTime.now());
                device.setUpdatedAt(LocalDateTime.now());

                // 更新额外配置
                Map<String, Object> config = device.getExtraConfig();
                if (config == null) {
                    config = new HashMap<>();
                }
                
                // 如果当前正在分析运行，且有实时数据，则更新推理 Fps 等数据
                StreamData realData = streamDataMap.get(streamId.toLowerCase());
                if (realData != null) {
                    config.put("selectedModel", realData.getSelectedModel());
                    config.put("inferenceFps", realData.getInferenceFps());
                    config.put("decodeFps", realData.getDecodeFps());
                    config.put("device", realData.getDevice());
                    config.put("counts", realData.getCounts());
                    config.put("detections", realData.getDetections());
                    config.put("timestamp", realData.getTimestamp());
                } else {
                    config.put("selectedModel", stream.getSelectedModel());
                    config.put("inferenceFps", stream.getInferenceFps() != null ? stream.getInferenceFps() : 0);
                    config.put("decodeFps", stream.getDecodeFps() != null ? stream.getDecodeFps() : 0);
                    config.put("device", stream.getDevice());
                    config.put("counts", new HashMap<>());
                    config.put("detections", new ArrayList<>());
                    config.put("timestamp", LocalDateTime.now().toString());
                }
                
                // 如果是在线运行状态，播放地址设为 ZLM 推推理流地址，否则设为原始 inputUrl
                if (isRunning) {
                    config.put("streamUrl", "rtsp://127.0.0.1:554/live/" + streamId + "_yolo");
                } else {
                    config.put("streamUrl", stream.getInputUrl());
                }
                device.setExtraConfig(config);

                deviceService.updateDevice(device);

            } else {
                // 不存在，创建新的摄像头设备
                Device newDevice = new Device();
                newDevice.setDeviceCode(streamId);
                newDevice.setDeviceName(stream.getName());
                newDevice.setDeviceType("CAMERA");
                newDevice.setManufacturer("AI_PLATFORM");
                newDevice.setModel("YOLO_AGENT");
                
                // 解析 IP
                String ip = "127.0.0.1";
                if (stream.getInputUrl() != null) {
                    try {
                        java.net.URI uri = java.net.URI.create(stream.getInputUrl().replace("rtsp://", "http://"));
                        if (uri.getHost() != null) {
                            ip = uri.getHost();
                        }
                    } catch (Exception ignored) {}
                }
                newDevice.setIpAddress(ip);
                newDevice.setPort(554);
                newDevice.setWaterAreaId(1L); // 默认重点区域
                newDevice.setLocation("POINT(120.0318156 28.5093682)"); // 默认村落地标坐标
                newDevice.setStatus(newStatus);
                newDevice.setLastHeartbeat(LocalDateTime.now());
                newDevice.setCreatedAt(LocalDateTime.now());
                newDevice.setUpdatedAt(LocalDateTime.now());
                newDevice.setDeleted(0);

                Map<String, Object> config = new HashMap<>();
                config.put("selectedModel", stream.getSelectedModel());
                config.put("inferenceFps", stream.getInferenceFps() != null ? stream.getInferenceFps() : 0);
                config.put("decodeFps", stream.getDecodeFps() != null ? stream.getDecodeFps() : 0);
                config.put("device", stream.getDevice());
                config.put("counts", new HashMap<>());
                config.put("detections", new ArrayList<>());
                
                if (isRunning) {
                    config.put("streamUrl", "rtsp://127.0.0.1:554/live/" + streamId + "_yolo");
                } else {
                    config.put("streamUrl", stream.getInputUrl());
                }
                newDevice.setExtraConfig(config);

                deviceService.createDevice(newDevice);
                log.info("同步到视频处理平台新接入的通道，创建大屏摄像头设备: {}", streamId);
            }
        }

        // 5. 对大屏端多余的摄像头进行软删除
        for (Device camera : cameras) {
            String code = camera.getDeviceCode();
            if (code != null && !activeStreamIds.contains(code.toLowerCase())) {
                camera.setDeleted(1);
                camera.setUpdatedAt(LocalDateTime.now());
                deviceService.updateDevice(camera);
                log.info("视频处理平台已删除该通道，同步软删除大屏摄像头设备: {}", code);
            }
        }

        // 风险任务由 RiskTaskService 按持久化红标告警独立同步。
    }
}
