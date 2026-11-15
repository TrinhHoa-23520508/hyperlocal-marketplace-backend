package vn.edu.uit.nearby.nearby_backend.identity.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param identifier the phone number, or an email address verified on the account
 */
public record LoginRequest(@NotBlank @Size(max = 254) String identifier, @NotBlank @Size(max = 200) String password) {

}
