package vn.edu.uit.nearby.nearby_backend.listing.web.dto;

import org.jspecify.annotations.Nullable;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.uit.nearby.nearby_backend.listing.domain.Category;

public record CategoryResponse(short id, @Schema(example = "ELECTRONICS") String code,
		@Schema(example = "Đồ điện tử") String name, @Nullable String iconKey) {

	public static CategoryResponse from(Category category) {
		return new CategoryResponse(category.getId(), category.getCode(), category.getName(), category.getIconKey());
	}

}
