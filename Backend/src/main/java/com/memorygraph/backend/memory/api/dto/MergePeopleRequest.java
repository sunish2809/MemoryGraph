package com.memorygraph.backend.memory.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record MergePeopleRequest(@NotNull UUID sourcePersonId) {
}
