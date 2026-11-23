package vn.edu.uit.nearby.nearby_backend.chat.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(description = "The newest message the reader has seen (FR-CH-06); an older marker is ignored")
public record MarkReadRequest(@Schema(example = "1042") @NotNull @PositiveOrZero Long lastReadMessageId) {

}
