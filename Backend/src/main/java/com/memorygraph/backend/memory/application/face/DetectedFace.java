package com.memorygraph.backend.memory.application.face;

/**
 * One face returned by the local InsightFace sidecar.
 *
 * @param x         normalised left (0–1)
 * @param y         normalised top (0–1)
 * @param width     normalised width (0–1)
 * @param height    normalised height (0–1)
 * @param embedding 512-d face embedding
 */
public record DetectedFace(double x, double y, double width, double height, float[] embedding) {
}
