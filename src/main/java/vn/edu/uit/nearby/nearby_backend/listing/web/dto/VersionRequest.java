package vn.edu.uit.nearby.nearby_backend.listing.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "The listing version the seller saw (optimistic lock, FR-LS-13)")
public record VersionRequest(@NotNull Long version) {

}
