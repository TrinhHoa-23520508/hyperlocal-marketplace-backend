package vn.edu.uit.nearby.nearby_backend.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendOtpRequest(@Schema(description = "Vietnamese mobile number; spaces, dots and dashes allowed",
		example = "0912 345 678") @NotBlank @Size(max = 20) String phone) {

}
