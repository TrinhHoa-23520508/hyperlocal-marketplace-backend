package vn.edu.uit.nearby.nearby_backend.listing.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.uit.nearby.nearby_backend.listing.domain.Category;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;

public interface CategoryRepository extends JpaRepository<Category, Short> {

	List<Category> findByActiveTrueOrderBySortOrder();

	/**
	 * @throws InvalidRequestException {@code VALIDATION_FAILED} on {@code categoryId} for
	 * an unknown category (FR-DC-02 "danh mục không tồn tại")
	 */
	default Category getExisting(short categoryId) {
		return findById(categoryId)
			.orElseThrow(() -> InvalidRequestException.invalidField("categoryId", "Unknown category."));
	}

}
