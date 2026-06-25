package main

import (
	"encoding/json"
	"log"
	"os"
	"sync"
	"time"

	"github.com/nats-io/nats.go"
)

type BoundingBox struct {
	X1 float64 `json:"x1"`
	Y1 float64 `json:"y1"`
	X2 float64 `json:"x2"`
	Y2 float64 `json:"y2"`
}

type Detection struct {
	Label      string      `json:"label"`
	Confidence float64     `json:"confidence"`
	BBox       BoundingBox `json:"bbox"`
}

type DetectionMessage struct {
	StreamID   string      `json:"stream_id"`
	FrameID    int64       `json:"frame_id"`
	Timestamp  string      `json:"timestamp"`
	Detections []Detection `json:"detections"`
}

type AlertMessage struct {
	StreamID  string    `json:"stream_id"`
	FrameID   int64     `json:"frame_id"`
	Timestamp string    `json:"timestamp"`
	Label     string    `json:"label"`
	Details   string    `json:"details"`
}

type WorkflowConfig struct {
	ID    string   `json:"id"`
	Name  string   `json:"name"`
	Nodes []string `json:"nodes"`
}

type WorkflowService struct {
	nc        *nats.Conn
	mu        sync.Mutex
	workflows map[string]WorkflowConfig
}

func main() {
	natsURL := os.Getenv("NATS_URL")
	if natsURL == "" {
		natsURL = nats.DefaultURL
	}

	log.Printf("Workflow Service connecting to NATS at %s...", natsURL)
	var nc *nats.Conn
	var err error
	for i := 0; i < 5; i++ {
		nc, err = nats.Connect(natsURL)
		if err == nil {
			break
		}
		log.Printf("NATS connection failed: %v. Retrying in 2s...", err)
		time.Sleep(2 * time.Second)
	}
	if err != nil {
		log.Fatalf("Could not connect to NATS: %v", err)
	}
	defer nc.Close()

	wfs := &WorkflowService{
		nc:        nc,
		workflows: make(map[string]WorkflowConfig),
	}

	// Subscribe to workflow configs
	_, err = nc.Subscribe("workflow.command.update", func(msg *nats.Msg) {
		var wf WorkflowConfig
		if err := json.Unmarshal(msg.Data, &wf); err != nil {
			log.Printf("Error unmarshalling workflow config: %v", err)
			return
		}
		wfs.mu.Lock()
		wfs.workflows[wf.ID] = wf
		wfs.mu.Unlock()
		log.Printf("Updated config for workflow: %s", wf.ID)
	})
	if err != nil {
		log.Fatalf("Subscribe workflow.command.update failed: %v", err)
	}

	// Subscribe to detection events from all streams
	_, err = nc.Subscribe("video.detections.*", func(msg *nats.Msg) {
		var dm DetectionMessage
		if err := json.Unmarshal(msg.Data, &dm); err != nil {
			log.Printf("Error unmarshalling detection message: %v", err)
			return
		}
		wfs.ProcessDetections(dm)
	})
	if err != nil {
		log.Fatalf("Subscribe video.detections.* failed: %v", err)
	}

	log.Println("Workflow Service is running. Monitoring detections...")
	select {} // block forever
}

func (wfs *WorkflowService) ProcessDetections(dm DetectionMessage) {
	// Simple rule execution (e.g. if 'person' is detected, issue an alert)
	for _, det := range dm.Detections {
		if det.Label == "person" && det.Confidence > 0.5 {
			alert := AlertMessage{
				StreamID:  dm.StreamID,
				FrameID:   dm.FrameID,
				Timestamp: time.Now().UTC().Format(time.RFC3339),
				Label:     det.Label,
				Details:   "Person detected inside prohibited zone",
			}

			log.Printf("[ALERT] Stream %s: Person detected with confidence %.2f at frame %d", dm.StreamID, det.Confidence, dm.FrameID)

			data, err := json.Marshal(alert)
			if err != nil {
				log.Printf("Failed to marshal alert message: %v", err)
				continue
			}

			if err := wfs.nc.Publish("video.alerts", data); err != nil {
				log.Printf("Failed to publish alert to NATS: %v", err)
			}
		}
	}
}
