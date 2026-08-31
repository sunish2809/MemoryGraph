package com.memorygraph.backend.memory.api.dto;

import java.util.List;
import java.util.UUID;

/** People graph for the richer UX: nodes are people; edges mean they co-occur on memories. */
public record PeopleGraphResponse(
        List<Node> nodes,
        List<Edge> edges) {

    public record Node(UUID id, String displayName, long memoryCount) {
    }

    public record Edge(UUID fromPersonId, UUID toPersonId, long sharedMemories) {
    }
}
