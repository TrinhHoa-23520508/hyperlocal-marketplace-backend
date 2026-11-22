package vn.edu.uit.nearby.nearby_backend.areaverification.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameAreaRequest(@Schema(example = "Công ty") @NotBlank @Size(max = 30) String label) {
}
