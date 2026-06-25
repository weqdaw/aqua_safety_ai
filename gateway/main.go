package main

import (
	"encoding/json"
	"log"
	"net/http"
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

type WorkflowConfig struct {
	ID    string   `json:"id"`
	Name  string   `json:"name"`
	Nodes []string `json:"nodes"`
}

type Gateway struct {
	nc        *nats.Conn
	mu        sync.Mutex
	streams   map[string]StreamConfig
	workflows map[string]WorkflowConfig
}

func main() {
	natsURL := os.Getenv("NATS_URL")
	if natsURL == "" {
		natsURL = nats.DefaultURL
	}

	log.Printf("Connecting to NATS at %s...", natsURL)
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

	gw := &Gateway{
		nc:        nc,
		streams:   make(map[string]StreamConfig),
		workflows: make(map[string]WorkflowConfig),
	}

	// Register some initial mock data
	gw.streams["stream-1"] = StreamConfig{
		ID:   "stream-1",
		Name: "Main Camera",
		URL:  "rtsp://mock-stream-source:8554/live",
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/api/v1/health", gw.handleHealth)
	mux.HandleFunc("/api/v1/streams", gw.handleStreams)
	mux.HandleFunc("/api/v1/streams/start", gw.handleStreamStart)
	mux.HandleFunc("/api/v1/streams/stop", gw.handleStreamStop)
	mux.HandleFunc("/api/v1/workflows", gw.handleWorkflows)

	// Simple CORS wrapper
	corsHandler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type")
		if r.Method == "OPTIONS" {
			w.WriteHeader(http.StatusOK)
			return
		}
		mux.ServeHTTP(w, r)
	})

	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}
	log.Printf("Gateway HTTP server listening on port %s...", port)
	if err := http.ListenAndServe(":"+port, corsHandler); err != nil {
		log.Fatalf("HTTP server failed: %v", err)
	}
}

func (gw *Gateway) handleHealth(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "healthy"})
}

func (gw *Gateway) handleStreams(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	gw.mu.Lock()
	defer gw.mu.Unlock()

	if r.Method == http.MethodGet {
		list := make([]StreamConfig, 0, len(gw.streams))
		for _, s := range gw.streams {
			list = append(list, s)
		}
		json.NewEncoder(w).Encode(list)
		return
	}

	if r.Method == http.MethodPost {
		var s StreamConfig
		if err := json.NewDecoder(r.Body).Decode(&s); err != nil {
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}
		if s.ID == "" {
			http.Error(w, "stream id is required", http.StatusBadRequest)
			return
		}
		gw.streams[s.ID] = s
		w.WriteHeader(http.StatusCreated)
		json.NewEncoder(w).Encode(s)
		return
	}

	http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
}

func (gw *Gateway) handleStreamStart(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req struct {
		ID string `json:"id"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	gw.mu.Lock()
	stream, exists := gw.streams[req.ID]
	gw.mu.Unlock()

	if !exists {
		http.Error(w, "Stream not found", http.StatusNotFound)
		return
	}

	// Publish start command to NATS
	payload, _ := json.Marshal(stream)
	if err := gw.nc.Publish("stream.command.start", payload); err != nil {
		http.Error(w, "Failed to publish start command: "+err.Error(), http.StatusInternalServerError)
		return
	}

	log.Printf("Published stream.command.start for stream %s", req.ID)
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{"status": "starting", "stream_id": req.ID})
}

func (gw *Gateway) handleStreamStop(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req struct {
		ID string `json:"id"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	// Publish stop command to NATS
	payload, _ := json.Marshal(map[string]string{"id": req.ID})
	if err := gw.nc.Publish("stream.command.stop", payload); err != nil {
		http.Error(w, "Failed to publish stop command: "+err.Error(), http.StatusInternalServerError)
		return
	}

	log.Printf("Published stream.command.stop for stream %s", req.ID)
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{"status": "stopping", "stream_id": req.ID})
}

func (gw *Gateway) handleWorkflows(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	gw.mu.Lock()
	defer gw.mu.Unlock()

	if r.Method == http.MethodGet {
		list := make([]WorkflowConfig, 0, len(gw.workflows))
		for _, wf := range gw.workflows {
			list = append(list, wf)
		}
		json.NewEncoder(w).Encode(list)
		return
	}

	if r.Method == http.MethodPost {
		var wf WorkflowConfig
		if err := json.NewDecoder(r.Body).Decode(&wf); err != nil {
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}
		if wf.ID == "" {
			http.Error(w, "workflow id is required", http.StatusBadRequest)
			return
		}
		gw.workflows[wf.ID] = wf
		// Notify workflow service of updated workflow
		payload, _ := json.Marshal(wf)
		gw.nc.Publish("workflow.command.update", payload)

		w.WriteHeader(http.StatusCreated)
		json.NewEncoder(w).Encode(wf)
		return
	}

	http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
}
