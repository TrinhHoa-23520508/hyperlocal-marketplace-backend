package vn.edu.uit.nearby.nearby_backend.identity.web;

import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import vn.edu.uit.nearby.nearby_backend.identity.application.block.BlockService;
import vn.edu.uit.nearby.nearby_backend.identity.web.dto.BlockedUserResponse;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ApiPaths;
import vn.edu.uit.nearby.nearby_backend.shared.security.CurrentUser;
import vn.edu.uit.nearby.nearby_backend.shared.web.CursorPage;

@Tag(name = "Blocks", description = "Block and unblock other users (FR-ID-14)")
@RestController
@RequestMapping(ApiPaths.CURRENT_USER_BASE + "/blocks")
public class BlockController {

	private final BlockService blockService;

	public BlockController(BlockService blockService) {
		this.blockService = blockService;
	}

	@Operation(summary = "List the users I blocked, most recent first")
	@GetMapping
	@ApiResponse(responseCode = "400", description = "INVALID_CURSOR")
	public CursorPage<BlockedUserResponse> listBlocked(@RequestParam(required = false) @Nullable String cursor,
			@RequestParam(required = false) @Nullable Integer limit) {
		return blockService.listBlocked(CurrentUser.requireId(), cursor, limit).map(BlockedUserResponse::from);
	}

	@Operation(summary = "Block a user; blocking again changes nothing")
	@PutMapping("/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@ApiResponse(responseCode = "400", description = "CANNOT_BLOCK_SELF")
	@ApiResponse(responseCode = "404", description = "USER_NOT_FOUND: unknown or deleted account")
	public void block(@PathVariable UUID userId) {
		blockService.block(CurrentUser.requireId(), userId);
	}

	@Operation(summary = "Unblock a user; unblocking a user who is not blocked changes nothing")
	@DeleteMapping("/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unblock(@PathVariable UUID userId) {
		blockService.unblock(CurrentUser.requireId(), userId);
	}

}
