package main

import (
	"encoding/json"
	"log"
	"os"
	"sync"
	"time"

	"github.com/nats-io/nats.go"
)

type StreamConfig struct {
	ID   string `json:"id"`
	Name string `json:"name"`
	URL  string `json:"url"`
}

type FrameMessage struct {
	StreamID  string `json:"stream_id"`
	FrameID   int64  `json:"frame_id"`
	Timestamp string `json:"timestamp"`
	Width     int    `json:"width"`
	Height    int    `json:"height"`
	MockData  string `json:"mock_data"` // base64 representation or simple indicator
}

type StreamService struct {
	nc       *nats.Conn
	mu       sync.Mutex
	active   map[string]chan struct{}
}

func main() {
	natsURL := os.Getenv("NATS_URL")
	if natsURL == "" {
		natsURL = nats.DefaultURL
	}

	log.Printf("Stream Service connecting to NATS at %s...", natsURL)
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

	svc := &StreamService{
		nc:     nc,
		active: make(map[string]chan struct{}),
	}

	// Subscribe to start command
	_, err = nc.Subscribe("stream.command.start", func(msg *nats.Msg) {
		var config StreamConfig
		if err := json.Unmarshal(msg.Data, &config); err != nil {
			log.Printf("Error unmarshalling start command: %v", err)
			return
		}
		svc.StartStream(config)
	})
	if err != nil {
		log.Fatalf("Subscribe start command failed: %v", err)
	}

	// Subscribe to stop command
	_, err = nc.Subscribe("stream.command.stop", func(msg *nats.Msg) {
		var req struct {
			ID string `json:"id"`
		}
		if err := json.Unmarshal(msg.Data, &req); err != nil {
			log.Printf("Error unmarshalling stop command: %v", err)
			return
		}
		svc.StopStream(req.ID)
	})
	if err != nil {
		log.Fatalf("Subscribe stop command failed: %v", err)
	}

	log.Println("Stream Service is running. Listening for control commands...")
	select {} // block forever
}

func (svc *StreamService) StartStream(cfg StreamConfig) {
	svc.mu.Lock()
	defer svc.mu.Unlock()

	// If already running, stop first
	if stopChan, exists := svc.active[cfg.ID]; exists {
		close(stopChan)
		delete(svc.active, cfg.ID)
	}

	stopChan := make(chan struct{})
	svc.active[cfg.ID] = stopChan

	go svc.simulateStreamLoop(cfg, stopChan)
	log.Printf("Started simulation for stream: %s (%s)", cfg.Name, cfg.ID)
}

func (svc *StreamService) StopStream(id string) {
	svc.mu.Lock()
	defer svc.mu.Unlock()

	if stopChan, exists := svc.active[id]; exists {
		close(stopChan)
		delete(svc.active, id)
		log.Printf("Stopped simulation for stream: %s", id)
	} else {
		log.Printf("Stream %s is not currently running", id)
	}
}

func (svc *StreamService) simulateStreamLoop(cfg StreamConfig, stopChan chan struct{}) {
	ticker := time.NewTicker(100 * time.Millisecond) // 10 FPS
	defer ticker.Stop()

	var frameID int64 = 0
	// Minimal transparent 1x1 PNG mock base64 to look realistic
	mockBase64 := "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="

	for {
		select {
		case <-stopChan:
			return
		case <-ticker.C:
			frameID++
			frameMsg := FrameMessage{
				StreamID:  cfg.ID,
				FrameID:   frameID,
				Timestamp: time.Now().UTC().Format(time.RFC3339Nano),
				Width:     1920,
				Height:    1080,
				MockData:  mockBase64,
			}

			data, err := json.Marshal(frameMsg)
			if err != nil {
				log.Printf("Failed to marshal frame message: %v", err)
				continue
			}

			// Publish frame to subject: video.frames.<stream_id>
			subject := "video.frames." + cfg.ID
			if err := svc.nc.Publish(subject, data); err != nil {
				log.Printf("Failed to publish frame %d for %s: %v", frameID, cfg.ID, err)
			}
		}
	}
}
