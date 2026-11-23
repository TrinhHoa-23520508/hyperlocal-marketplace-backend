package vn.edu.uit.nearby.nearby_backend.listing.web.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.uit.nearby.nearby_backend.listing.application.ListingImageService.UploadTicket;

@Schema(description = "Upload the bytes with PUT to uploadUrl and Content-Type = contentType, then call complete")
public record UploadTicketResponse(UUID imageId, String uploadUrl, @Schema(example = "PUT") String uploadMethod,
		@Schema(example = "image/jpeg") String contentType, Instant expiresAt, long maxSizeBytes) {

	public static UploadTicketResponse from(UploadTicket ticket) {
		return new UploadTicketResponse(ticket.imageId(), ticket.uploadUrl(), ticket.uploadMethod(),
				ticket.contentType(), ticket.expiresAt(), ticket.maxSizeBytes());
	}

}
