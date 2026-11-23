package vn.edu.uit.nearby.nearby_backend.chat.web;

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
import vn.edu.uit.nearby.nearby_backend.chat.application.ChatImageService;
import vn.edu.uit.nearby.nearby_backend.chat.application.ChatImageService.ImageStatusView;
import vn.edu.uit.nearby.nearby_backend.chat.application.ChatImageService.UploadTicket;
import vn.edu.uit.nearby.nearby_backend.chat.web.dto.ChatImageUploadRequest;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ApiPaths;
import vn.edu.uit.nearby.nearby_backend.shared.security.CurrentUser;

@Tag(name = "Chat images",
		description = "Upload the images of an IMAGE message; they are checked and re-encoded without EXIF/GPS "
				+ "(FR-CH-04, UC-CH-03, UC-SY-10; A18)")
@RestController
@RequestMapping(ApiPaths.CHAT_IMAGES_BASE)
public class ChatImageController {

	private final ChatImageService images;

	public ChatImageController(ChatImageService images) {
		this.images = images;
	}

	@Operation(summary = "A presigned PUT URL for one image of a conversation, under the private chat-incoming/ prefix")
	@PostMapping("/upload-url")
	@ApiResponse(responseCode = "400",
			description = "INVALID_IMAGE: not JPEG, PNG or WebP (convert HEIC to JPEG), or over 5 242 880 bytes")
	@ApiResponse(responseCode = "404", description = "CONVERSATION_NOT_FOUND: not a member")
	public UploadTicket requestUploadUrl(@Valid @RequestBody ChatImageUploadRequest request) {
		return images.requestUpload(CurrentUser.requireId(), request.conversationId(), request.contentType(),
				request.sizeBytes());
	}

	@Operation(summary = "Upload done: processing starts; poll the status until READY or REJECTED",
			description = "Calling it again after processing returns the current status. A REJECTED image shows the retry button.")
	@PostMapping("/{imageId}/complete")
	@ResponseStatus(HttpStatus.ACCEPTED)
	@ApiResponse(responseCode = "400",
			description = "INVALID_IMAGE: nothing was uploaded, or the object is over the limit (it is deleted)")
	@ApiResponse(responseCode = "404", description = "IMAGE_NOT_FOUND: not one of the caller's images")
	public ImageStatusView completeUpload(@PathVariable UUID imageId) {
		return images.completeUpload(CurrentUser.requireId(), imageId);
	}

	@Operation(summary = "Status and short-lived links of an image",
			description = "The sender sees it from the upload on; the other member only once a message uses it.")
	@GetMapping("/{imageId}")
	@ApiResponse(responseCode = "404", description = "IMAGE_NOT_FOUND: not a member, or not sent yet")
	public ImageStatusView status(@PathVariable UUID imageId) {
		return images.view(CurrentUser.requireId(), imageId);
	}

}
