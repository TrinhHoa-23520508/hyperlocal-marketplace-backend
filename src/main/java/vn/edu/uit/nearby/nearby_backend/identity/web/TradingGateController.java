package vn.edu.uit.nearby.nearby_backend.identity.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import vn.edu.uit.nearby.nearby_backend.identity.TradingGate;
import vn.edu.uit.nearby.nearby_backend.identity.web.dto.TradingGateResponse;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ApiPaths;
import vn.edu.uit.nearby.nearby_backend.shared.security.CurrentUser;

@Tag(name = "Trading gate", description = "Conditions to post listings, chat and make offers (FR-ID-20)")
@RestController
@RequestMapping(ApiPaths.CURRENT_USER_BASE + "/trading-gate")
public class TradingGateController {

	private final TradingGate tradingGate;

	public TradingGateController(TradingGate tradingGate) {
		this.tradingGate = tradingGate;
	}

	@Operation(summary = "Whether the user may trade, and which steps are missing")
	@GetMapping
	public TradingGateResponse getTradingGate() {
		return TradingGateResponse.from(tradingGate.evaluate(CurrentUser.requireId()));
	}

}
