# VideoFlow AI

## Open Source Visual AI Runtime Platform

### Vision

VideoFlow AI 是一个面向计算机视觉开发者、机器人开发者、无人机开发者和工业AI团队的开源视觉AI运行平台。

项目目标：

构建一个统一的视频流接入、模型推理、规则编排和结果输出平台，使开发者无需重复编写视频接入、解码、推流和推理调度代码，即可快速搭建视觉AI应用。

---

# Core Features

## Unified Video Access

支持多种视频源接入：

- RTSP

- RTMP

- WebRTC

- GB28181

- SRT

- USB Camera

- Local Video File

- DJI SDK (Future)

统一抽象为：

VideoSource

业务层无需关心底层协议。

---

## AI Plugin System

支持插件化模型管理。

内置支持：

- YOLO

- RT-DETR

- SAM2

- OCR

- Pose Estimation

- Object Tracking

支持第三方插件扩展。

统一接口：

load()

infer()

unload()

---

## Workflow Engine

支持可视化流程编排。

示例：

Camera

↓

YOLO

↓

OCR

↓

Counter

↓

Webhook

开发者可通过拖拽方式快速构建业务流程。

---

## Rule Engine

支持事件驱动规则配置。

例如：

- 连续出现3秒触发告警

- 指定区域入侵检测

- 越线检测

- 人数统计

- 安全帽检测

---

## Output System

支持：

- RTSP推流

- RTMP推流

- WebRTC推流

- MQTT

- Kafka

- Webhook

- Database Storage

---

# Technical Architecture

Frontend

React + TypeScript + React Flow

Backend

Go

AI Runtime

Python

Message Bus

NATS

Database

PostgreSQL

Cache

Redis

Video Processing

GStreamer

Inference Engine

PyTorch

TensorRT

ONNX Runtime

Deployment

Docker Compose

Kubernetes (Future)

---

# System Architecture

Frontend

↓

API Gateway

↓

Task Scheduler

↓

Message Bus (NATS)

↓

Stream Service

↓

Inference Service

↓

Workflow Engine

↓

Output Service

↓

Database / Stream / Webhook

---

# Repository Structure

videoflow-ai/

frontend/

gateway/

stream-service/

workflow-service/

inference-worker/

plugin-sdk/

plugins/

docker/

docs/

scripts/

examples/

---

# Development Roadmap

V1

- RTSP接入

- YOLO推理

- Web展示

- Docker部署

V2

- 多路视频

- 插件系统

- RTMP支持

V3

- WebRTC支持

- OCR插件

- Tracking插件

V4

- 可视化工作流

- 规则引擎

V5

- GB28181支持

- SRT支持

V6

- 分布式推理

- GPU调度

- 集群部署

---

# Target Users

- Computer Vision Developers

- AI Researchers

- Robotics Engineers

- Drone Developers

- Smart City Teams

- Industrial Inspection Teams

- University Laboratories

---

# Long-Term Goal

成为视觉AI领域的基础设施平台。

让开发者专注于算法和业务逻辑，而不是重复开发视频接入、推理调度和结果输出系统。
