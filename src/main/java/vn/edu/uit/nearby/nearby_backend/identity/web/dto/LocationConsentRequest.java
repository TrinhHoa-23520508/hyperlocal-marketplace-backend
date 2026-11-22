package vn.edu.uit.nearby.nearby_backend.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LocationConsentRequest(@Schema(description = "Must equal currentTermsVersion from GET",
		example = "1.0") @NotBlank @Size(max = 20) String termsVersion) {

}
