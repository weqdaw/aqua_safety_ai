import asyncio
import json
import os
import sys
import base64
from nats.aio.client import Client as NATS

# Add relevant directories to path
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '../plugin-sdk')))
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '../plugins/yolov8')))

from yolov8_plugin import YOLOv8Plugin

class InferenceWorker:
    def __init__(self, nats_url: str):
        self.nats_url = nats_url
        self.nc = NATS()
        self.plugin = YOLOv8Plugin()

    async def run(self):
        # Connect to NATS
        print(f"Inference Worker connecting to NATS at {self.nats_url}...")
        for attempt in range(5):
            try:
                await self.nc.connect(self.nats_url)
                print("Inference Worker connected to NATS.")
                break
            except Exception as e:
                print(f"Connection attempt {attempt + 1} failed: {e}. Retrying in 2s...")
                await asyncio.sleep(2)
        else:
            print("Failed to connect to NATS. Exiting.")
            sys.exit(1)

        # Load plugin
        self.plugin.load()

        # Subscribe to video.frames.*
        # Wildcard * matches the stream_id
        async def frame_handler(msg):
            subject = msg.subject
            # Extract stream_id from subject e.g. "video.frames.stream-1"
            parts = subject.split('.')
            stream_id = parts[-1] if len(parts) > 2 else "unknown"

            try:
                payload = json.loads(msg.data.decode())
                # Extract mock image bytes
                mock_data_base64 = payload.get("mock_data", "")
                frame_bytes = base64.b64decode(mock_data_base64) if mock_data_base64 else b""

                # Run plugin inference
                detections = self.plugin.infer(payload, frame_bytes)

                # Prepare detection response
                detection_msg = {
                    "stream_id": stream_id,
                    "frame_id": payload.get("frame_id", 0),
                    "timestamp": payload.get("timestamp", ""),
                    "detections": detections
                }

                # Publish detection results to video.detections.<stream_id>
                dest_subject = f"video.detections.{stream_id}"
                await self.nc.publish(dest_subject, json.dumps(detection_msg).encode())
                
                if detections:
                    print(f"Inference Worker: Processed frame {payload.get('frame_id')} for {stream_id}. Detected: {[d['label'] for d in detections]}")
            except Exception as e:
                print(f"Error processing frame: {e}")

        await self.nc.subscribe("video.frames.*", cb=frame_handler)
        print("Inference Worker is running. Subscribed to video.frames.*")

        # Keep running
        while True:
            await asyncio.sleep(1)

    async def close(self):
        self.plugin.unload()
        await self.nc.close()

if __name__ == "__main__":
    nats_addr = os.getenv("NATS_URL", "nats://localhost:4222")
    worker = InferenceWorker(nats_addr)
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    try:
        loop.run_until_complete(worker.run())
    except KeyboardInterrupt:
        print("Stopping Inference Worker...")
    finally:
        loop.run_until_complete(worker.close())
        loop.close()
