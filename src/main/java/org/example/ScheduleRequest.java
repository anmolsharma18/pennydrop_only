package org.example;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record ScheduleRequest(@NotNull @Valid RunInput input, @NotBlank String scheduledLocalTime) {}
