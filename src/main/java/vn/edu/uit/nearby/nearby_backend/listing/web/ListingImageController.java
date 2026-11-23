package vn.edu.uit.nearby.nearby_backend.listing.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import vn.edu.uit.nearby.nearby_backend.listing.application.ListingImageService;
import vn.edu.uit.nearby.nearby_backend.listing.web.dto.ImageStatusResponse;
import vn.edu.uit.nearby.nearby_backend.listing.web.dto.UploadTicketResponse;
import vn.edu.uit.nearby.nearby_backend.listing.web.dto.UploadUrlRequest;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ApiPaths;
import vn.edu.uit.nearby.nearby_backend.shared.security.CurrentUser;

@Tag(name = "Listing images",
		description = "Upload listing images; they are checked and re-encoded without EXIF/GPS (FR-LS-02, UC-SY-10; A13)")
@RestController
@RequestMapping(ApiPaths.LISTING_IMAGES_BASE)
public class ListingImageController {

	private final ListingImageService images;

	public ListingImageController(ListingImageService images) {
		this.images = images;
	}

	@Operation(summary = "A presigned PUT URL for one image under the private incoming/ prefix")
	@PostMapping("/upload-url")
	@ApiResponse(responseCode = "400",
			description = "INVALID_IMAGE: not JPEG, PNG or WebP (convert HEIC to JPEG), or over 5 242 880 bytes")
	public UploadTicketResponse requestUploadUrl(@Valid @RequestBody UploadUrlRequest request) {
		return UploadTicketResponse
			.from(images.requestUpload(CurrentUser.requireId(), request.contentType(), request.sizeBytes()));
	}

	@Operation(summary = "Upload done: processing starts; poll the status until READY or REJECTED",
			description = "Calling it again after processing returns the current status.")
	@PostMapping("/{imageId}/complete")
	@ResponseStatus(HttpStatus.ACCEPTED)
	@ApiResponse(responseCode = "400",
			description = "INVALID_IMAGE: nothing was uploaded, or the object is over the limit (it is deleted)")
	@ApiResponse(responseCode = "404", description = "IMAGE_NOT_FOUND: not one of the caller's images")
	public ImageStatusResponse completeUpload(@PathVariable UUID imageId) {
		return ImageStatusResponse.from(images.completeUpload(CurrentUser.requireId(), imageId));
	}

	@Operation(summary = "Processing status of an uploaded image (UC-LS-01 A5)")
	@GetMapping("/{imageId}")
	@ApiResponse(responseCode = "404", description = "IMAGE_NOT_FOUND: not one of the caller's images")
	public ImageStatusResponse status(@PathVariable UUID imageId) {
		return ImageStatusResponse.from(images.status(CurrentUser.requireId(), imageId));
	}

}
