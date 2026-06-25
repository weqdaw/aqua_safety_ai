import { useState, useEffect, useRef } from 'react';
import './App.css';
import cameraFeedMock from './assets/camera_feed_mock.png';

interface Stream {
  id: string;
  name: string;
  url: string;
  status: 'stopped' | 'running';
}

interface LogEntry {
  time: string;
  type: 'info' | 'warning' | 'danger' | 'success';
  msg: string;
}

function App() {
  const [streams, setStreams] = useState<Stream[]>([
    { id: 'stream-1', name: 'Main Camera (RTSP)', url: 'rtsp://192.168.1.100:8554/live', status: 'stopped' }
  ]);
  const [activeStreamId, setActiveStreamId] = useState<string>('stream-1');
  const [logs, setLogs] = useState<LogEntry[]>([
    { time: new Date().toLocaleTimeString(), type: 'info', msg: 'VideoFlow AI platform initialized.' },
    { time: new Date().toLocaleTimeString(), type: 'info', msg: 'NATS connection established on port 4222.' },
    { time: new Date().toLocaleTimeString(), type: 'success', msg: 'System healthy. Ready to ingest stream sources.' }
  ]);

  const [metrics, setMetrics] = useState({
    activeStreams: 0,
    latency: '0ms',
    alerts: 0,
    fps: 0
  });

  const logsEndRef = useRef<HTMLDivElement>(null);
  const logTimerRef = useRef<number | null>(null);

  // Automatically scroll logs to bottom
  useEffect(() => {
    if (logsEndRef.current) {
      logsEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [logs]);

  // Handle stream state changes and metrics simulation
  const activeStream = streams.find(s => s.id === activeStreamId);
  const isRunning = activeStream?.status === 'running';

  useEffect(() => {
    if (isRunning) {
      setMetrics({
        activeStreams: streams.filter(s => s.status === 'running').length,
        latency: '14ms',
        fps: 25,
        alerts: metrics.alerts
      });

      // Periodically append mock detection logs when running
      logTimerRef.current = window.setInterval(() => {
        const timestamp = new Date().toLocaleTimeString();
        const rand = Math.random();
        let newLog: LogEntry;

        if (rand < 0.4) {
          newLog = {
            time: timestamp,
            type: 'info',
            msg: `Frame processed in 12ms (~25 FPS). 0 detections.`
          };
        } else if (rand < 0.7) {
          const confidence = Math.round((0.75 + Math.random() * 0.23) * 100);
          newLog = {
            time: timestamp,
            type: 'success',
            msg: `Inference success: Detected "car" with ${confidence}% confidence.`
          };
        } else {
          const confidence = Math.round((0.8 + Math.random() * 0.18) * 100);
          newLog = {
            time: timestamp,
            type: 'danger',
            msg: `ALERT: "person" detected with ${confidence}% confidence inside Restricted Zone!`
          };
          setMetrics(prev => ({ ...prev, alerts: prev.alerts + 1 }));
        }

        setLogs(prev => [...prev, newLog]);
      }, 1500);
    } else {
      setMetrics({
        activeStreams: streams.filter(s => s.status === 'running').length,
        latency: '0ms',
        fps: 0,
        alerts: metrics.alerts
      });
      if (logTimerRef.current) {
        clearInterval(logTimerRef.current);
        logTimerRef.current = null;
      }
    }

    return () => {
      if (logTimerRef.current) {
        clearInterval(logTimerRef.current);
      }
    };
  }, [isRunning, streams]);

  // Call backend endpoints to start stream simulation
  const startStream = async (id: string) => {
    const timestamp = new Date().toLocaleTimeString();
    
    // Optimistic UI update
    setStreams(prev => prev.map(s => s.id === id ? { ...s, status: 'running' } : s));
    setLogs(prev => [...prev, { time: timestamp, type: 'info', msg: `Sending start command for stream ${id}...` }]);

    try {
      const response = await fetch('http://localhost:9898/api/v1/streams/start', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id })
      });
      if (response.ok) {
        const data = await response.json();
        setLogs(prev => [...prev, { time: new Date().toLocaleTimeString(), type: 'success', msg: `Stream ${id} started successfully via backend. Status: ${data.status}` }]);
      } else {
        throw new Error('Server returned non-200');
      }
    } catch (err) {
      // Graceful fallback to mock local execution
      setLogs(prev => [
        ...prev,
        { time: new Date().toLocaleTimeString(), type: 'warning', msg: `Gateway offline. Running in local simulation mode.` },
        { time: new Date().toLocaleTimeString(), type: 'success', msg: `Stream ${id} running in client simulation.` }
      ]);
    }
  };

  const stopStream = async (id: string) => {
    const timestamp = new Date().toLocaleTimeString();
    
    // Optimistic UI update
    setStreams(prev => prev.map(s => s.id === id ? { ...s, status: 'stopped' } : s));
    setLogs(prev => [...prev, { time: timestamp, type: 'info', msg: `Sending stop command for stream ${id}...` }]);

    try {
      const response = await fetch('http://localhost:9898/api/v1/streams/stop', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id })
      });
      if (response.ok) {
        setLogs(prev => [...prev, { time: new Date().toLocaleTimeString(), type: 'info', msg: `Stream ${id} stopped via backend.` }]);
      } else {
        throw new Error('Server returned non-200');
      }
    } catch (err) {
      setLogs(prev => [
        ...prev,
        { time: new Date().toLocaleTimeString(), type: 'info', msg: `Stream ${id} stopped (local simulation ended).` }
      ]);
    }
  };

  return (
    <div className="dashboard-container">
      {/* Header */}
      <header className="dashboard-header">
        <div className="brand-section">
          <div className="logo-glow">VF</div>
          <div>
            <h1 className="brand-title">VideoFlow AI</h1>
            <div className="brand-subtitle">Visual AI Runtime Platform</div>
          </div>
        </div>
        <div className="system-status">
          <span className="pulse-dot"></span>
          <span>Core System Active</span>
        </div>
      </header>

      {/* Metrics Row */}
      <section className="metrics-grid">
        <div className="glass-panel metric-card">
          <div className="metric-info">
            <h3>Active Streams</h3>
            <p className="metric-value">{metrics.activeStreams}</p>
          </div>
          <div className="metric-icon-box primary">📹</div>
        </div>

        <div className="glass-panel metric-card">
          <div className="metric-info">
            <h3>Inference Latency</h3>
            <p className="metric-value">{metrics.latency}</p>
          </div>
          <div className="metric-icon-box accent">⚡</div>
        </div>

        <div className="glass-panel metric-card">
          <div className="metric-info">
            <h3>FPS Output</h3>
            <p className="metric-value">{metrics.fps}</p>
          </div>
          <div className="metric-icon-box warning">🎬</div>
        </div>

        <div className="glass-panel metric-card">
          <div className="metric-info">
            <h3>Alerts Triggered</h3>
            <p className="metric-value">{metrics.alerts}</p>
          </div>
          <div className="metric-icon-box danger">🚨</div>
        </div>
      </section>

      {/* Content Grid */}
      <div className="content-grid">
        
        {/* Left Column: Streams Control */}
        <section className="glass-panel">
          <div className="card-title-bar">
            <h2>Video Sources</h2>
          </div>
          <div className="card-body">
            {streams.map(stream => (
              <div 
                key={stream.id} 
                className={`stream-item ${stream.status === 'running' ? 'active' : ''} ${activeStreamId === stream.id ? 'selected' : ''}`}
                onClick={() => setActiveStreamId(stream.id)}
                style={{ cursor: 'pointer' }}
              >
                <div className="stream-meta">
                  <span className="stream-name">{stream.name}</span>
                  <span className={`pulse-dot ${stream.status === 'stopped' ? 'stopped' : ''}`}></span>
                </div>
                <div className="stream-url">{stream.url}</div>
                <div className="btn-group" onClick={(e) => e.stopPropagation()}>
                  {stream.status === 'stopped' ? (
                    <button className="btn btn-primary" onClick={() => startStream(stream.id)}>
                      ▶ Start Stream
                    </button>
                  ) : (
                    <button className="btn btn-danger" onClick={() => stopStream(stream.id)}>
                      ■ Stop Stream
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        </section>

        {/* Middle Column: Video Live feed & Workflow preview */}
        <section className="glass-panel">
          <div className="card-title-bar">
            <h2>Live Feed / Pipeline Configuration</h2>
          </div>
          <div className="card-body">
            <div className="preview-container">
              {isRunning ? (
                <>
                  <div className="hud-overlay">
                    <span className="pulse-dot"></span>
                    <span>LIVE: {activeStream?.name}</span>
                  </div>
                  <div className="scan-radar"></div>
                  <img src={cameraFeedMock} alt="Simulated Object Detection Stream" className="mock-feed-image" />
                </>
              ) : (
                <div className="no-feed">
                  <span className="no-feed-icon">🎥</span>
                  <span>Stream Offline. Click "Start Stream" to activate inference.</span>
                </div>
              )}
            </div>

            <h3 style={{ margin: '16px 0 8px 0', fontSize: '14px', color: 'var(--text-muted)' }}>Workflow DAG View</h3>
            <div className="workflow-canvas">
              <div className="flow-step">
                <div className={`flow-node ${isRunning ? 'active' : ''}`}>
                  <div className="node-header">Source</div>
                  <div className="node-title">{activeStream?.name}</div>
                  <div className="node-meta">RTSP Stream</div>
                </div>

                <div className={`connector-arrow ${isRunning ? 'active' : ''}`}>🡒</div>

                <div className={`flow-node ${isRunning ? 'active' : ''}`}>
                  <div className="node-header">Model Node</div>
                  <div className="node-title">YOLOv8 Object</div>
                  <div className="node-meta">Confidence &gt; 0.5</div>
                </div>

                <div className={`connector-arrow ${isRunning ? 'active' : ''}`}>🡒</div>

                <div className={`flow-node ${isRunning ? 'active' : ''}`}>
                  <div className="node-header">Action</div>
                  <div className="node-title">Console Alert</div>
                  <div className="node-meta">Web Hook Trigger</div>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* Right Column: Event Log Logs */}
        <section className="glass-panel">
          <div className="card-title-bar">
            <h2>System Events / Alert Logs</h2>
          </div>
          <div className="card-body">
            <div className="logs-console">
              {logs.map((log, index) => (
                <div key={index} className="log-entry">
                  <span className="log-time">[{log.time}]</span>
                  <span className={`log-type ${log.type}`}>{log.type.toUpperCase()}</span>
                  <span className="log-msg">{log.msg}</span>
                </div>
              ))}
              <div ref={logsEndRef} />
            </div>
          </div>
        </section>

      </div>
    </div>
  );
}

export default App;
