"""
Free local face detection for MemoryGraph using InsightFace (buffalo_s).
No cloud API keys — models download once into /models.
"""

from __future__ import annotations

import logging
from typing import Any

import cv2
import numpy as np
from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.responses import JSONResponse

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("faces")

app = FastAPI(title="MemoryGraph Faces", version="0.1.0")

_face_app = None


def get_face_app():
    global _face_app
    if _face_app is not None:
        return _face_app
    from insightface.app import FaceAnalysis

    analysis = FaceAnalysis(
        name="buffalo_s",
        root="/models",
        providers=["CPUExecutionProvider"],
    )
    analysis.prepare(ctx_id=-1, det_size=(640, 640))
    _face_app = analysis
    log.info("InsightFace buffalo_s ready")
    return _face_app


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/detect")
async def detect(file: UploadFile = File(...)) -> JSONResponse:
    raw = await file.read()
    if not raw:
        raise HTTPException(status_code=400, detail="Empty file")

    arr = np.frombuffer(raw, dtype=np.uint8)
    image = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if image is None:
        raise HTTPException(status_code=400, detail="Could not decode image")

    height, width = image.shape[:2]
    if height < 1 or width < 1:
        raise HTTPException(status_code=400, detail="Invalid image dimensions")

    try:
        faces = get_face_app().get(image)
    except Exception as ex:  # noqa: BLE001 — surface as empty set to the backend
        log.exception("Face detection failed: %s", ex)
        return JSONResponse({"faces": []})

    payload: list[dict[str, Any]] = []
    for face in faces:
        x1, y1, x2, y2 = [float(v) for v in face.bbox]
        x1 = max(0.0, min(float(width), x1))
        y1 = max(0.0, min(float(height), y1))
        x2 = max(0.0, min(float(width), x2))
        y2 = max(0.0, min(float(height), y2))
        bw = max(0.0, x2 - x1)
        bh = max(0.0, y2 - y1)
        embedding = face.normed_embedding
        if embedding is None:
            continue
        payload.append(
            {
                "bbox": [x1 / width, y1 / height, bw / width, bh / height],
                "embedding": [float(v) for v in embedding.tolist()],
            }
        )

    return JSONResponse({"faces": payload})
