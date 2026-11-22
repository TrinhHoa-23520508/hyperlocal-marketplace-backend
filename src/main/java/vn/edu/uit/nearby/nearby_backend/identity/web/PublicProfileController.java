package vn.edu.uit.nearby.nearby_backend.identity.web;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import vn.edu.uit.nearby.nearby_backend.identity.application.profile.ProfileService;
import vn.edu.uit.nearby.nearby_backend.identity.web.dto.PublicProfileResponse;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ApiPaths;

@Tag(name = "Public profiles", description = "What anyone can see about a user (FR-ID-13)")
@SecurityRequirements
@RestController
public class PublicProfileController {

	private final ProfileService profileService;

	public PublicProfileController(ProfileService profileService) {
		this.profileService = profileService;
	}

	@Operation(summary = "Get a user's public profile; no coordinates and no contact data")
	@GetMapping(ApiPaths.PUBLIC_PROFILE)
	@ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
	public PublicProfileResponse getPublicProfile(@PathVariable UUID userId) {
		return PublicProfileResponse.from(profileService.getPublicProfile(userId));
	}

}
