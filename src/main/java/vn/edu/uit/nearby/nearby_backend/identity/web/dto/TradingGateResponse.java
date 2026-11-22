package vn.edu.uit.nearby.nearby_backend.identity.web.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.uit.nearby.nearby_backend.identity.TradingGateFailure;
import vn.edu.uit.nearby.nearby_backend.identity.TradingGateStatus;

public record TradingGateResponse(
		@Schema(description = "True when the user may post listings, open chats and make offers") boolean open,
		@Schema(description = "Unmet conditions in FR-ID-20 order; empty when open") List<TradingGateFailure> failures) {

	public static TradingGateResponse from(TradingGateStatus status) {
		return new TradingGateResponse(status.isOpen(), status.failures());
	}

}
