from abc import ABC, abstractmethod
from typing import Dict, Any, List

class BasePlugin(ABC):
    @abstractmethod
    def load(self) -> None:
        """
        Initialize and load the AI model weights, configuration, or runtime.
        """
        pass

    @abstractmethod
    def infer(self, frame_metadata: Dict[str, Any], frame_bytes: bytes) -> List[Dict[str, Any]]:
        """
        Execute model inference on the given frame data.
        
        Args:
            frame_metadata: Metadata containing stream_id, frame_id, etc.
            frame_bytes: Decoded raw image bytes.
            
        Returns:
            A list of detections, e.g.:
            [
                {
                    "label": "person",
                    "confidence": 0.89,
                    "bbox": {"x1": 100, "y1": 150, "x2": 250, "y2": 400}
                }
            ]
        """
        pass

    @abstractmethod
    def unload(self) -> None:
        """
        Clean up resources, deallocate CUDA buffers, or unload the model.
        """
        pass
