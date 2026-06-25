import random
import time
import sys
import os

# Allow import of plugin-sdk
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '../../plugin-sdk')))
from base_plugin import BasePlugin

class YOLOv8Plugin(BasePlugin):
    def __init__(self):
        self.model_loaded = False

    def load(self) -> None:
        print("YOLOv8Plugin: Loading mock model weights...")
        time.sleep(0.5) # simulate minor loading latency
        self.model_loaded = True
        print("YOLOv8Plugin: Mock model loaded successfully.")

    def infer(self, frame_metadata: dict, frame_bytes: bytes) -> list:
        if not self.model_loaded:
            raise RuntimeWarning("Model not loaded yet.")

        # Simulate inference: randomly output detections
        detections = []
        
        # 30% chance to detect a person
        if random.random() < 0.3:
            detections.append({
                "label": "person",
                "confidence": round(random.uniform(0.6, 0.98), 2),
                "bbox": {
                    "x1": round(random.uniform(100, 300), 1),
                    "y1": round(random.uniform(100, 300), 1),
                    "x2": round(random.uniform(400, 600), 1),
                    "y2": round(random.uniform(400, 800), 1)
                }
            })
            
        # 20% chance to detect a car
        if random.random() < 0.2:
            detections.append({
                "label": "car",
                "confidence": round(random.uniform(0.5, 0.95), 2),
                "bbox": {
                    "x1": round(random.uniform(500, 700), 1),
                    "y1": round(random.uniform(200, 400), 1),
                    "x2": round(random.uniform(800, 1000), 1),
                    "y2": round(random.uniform(500, 700), 1)
                }
            })

        return detections

    def unload(self) -> None:
        print("YOLOv8Plugin: Unloading mock model...")
        self.model_loaded = False
