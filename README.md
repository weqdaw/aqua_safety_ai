# VideoFlow AI Skeleton

This repository represents the initial foundation and skeleton code for **VideoFlow AI** — an open-source visual AI runtime platform.

It is structured into separate microservices that communicate using a central **NATS** message broker, containerized using Docker Compose.

---

## Repository Structure

```
videoflow-ai/
├── docker-compose.yml       # Orchestrates all microservices
├── gateway/                 # Go API Gateway (HTTP REST -> NATS)
├── stream-service/          # Go service simulating video stream frame generation
├── workflow-service/        # Go service running rules and triggers on detections
├── inference-worker/        # Python AI inference worker running plugins
├── plugin-sdk/              # Python abstract interface for AI models
├── plugins/                 # AI model plugins (e.g., mock YOLOv8)
└── frontend/                # React + TypeScript + Vite Dashboard
```

---

## Services & Ports Map

| Service | Language / Tech | Description | Host Port / Address |
|---|---|---|---|
| **NATS** | Go (Docker) | High-performance message broker | `localhost:4222` |
| **Gateway** | Go (net/http) | REST API gateway | `localhost:9898` |
| **Frontend** | React (Nginx) | Management dashboard & HUD | `localhost:3000` |
| **Stream Service** | Go | Simulates RTSP frame emission | Inter-container |
| **Inference Worker** | Python | Dynamically loads AI plugins | Inter-container |
| **Workflow Service** | Go | Subscribes to events, issues alerts | Inter-container |

---

## How to Run & Verify

### Prerequisites
- [Docker](https://www.docker.com/) and [Docker Compose](https://docs.docker.com/compose/) installed on your machine.

### Spin Up the Application
At the root directory, run the following command to build and launch all containers in detached mode:

```bash
docker-compose up --build -d
```

### Accessing the Applications
1. **Frontend Dashboard**: Open your browser at [http://localhost:3000](http://localhost:3000) to access the VideoFlow AI interface.
2. **Gateway Health Check**: Open [http://localhost:9898/api/v1/health](http://localhost:9898/api/v1/health) to verify gateway connectivity.

### Verify Command Flows
When you click **"Start Stream"** on the dashboard:
1. The frontend hits `POST /api/v1/streams/start`.
2. The `gateway` publishes a start command on NATS (`stream.command.start`).
3. `stream-service` receives the command and starts publishing simulated frame data (subject: `video.frames.<stream_id>`).
4. `inference-worker` catches frames from `video.frames.*`, processes them through `yolov8_plugin`, and publishes detections on `video.detections.<stream_id>`.
5. `workflow-service` subscribes to detections, matches labels against its rule ruleset, and issues alerts (subject: `video.alerts`).
6. The frontend shows live telemetry, processing FPS, and incoming alerts.

To stop, click **"Stop Stream"** on the dashboard, which issues a stop request down the NATS broker pipeline.
