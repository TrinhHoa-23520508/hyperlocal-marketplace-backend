package vn.edu.uit.nearby.nearby_backend.chat.web.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Open the conversation about a listing (FR-CH-01, A10 \"Chat\")")
public record OpenConversationRequest(@NotNull UUID listingId) {

}
