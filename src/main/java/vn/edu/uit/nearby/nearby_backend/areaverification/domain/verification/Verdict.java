package vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification;

import java.util.ArrayList;
import java.util.List;

/**
 * Outcome of the anti-spoofing checks for one attempt (appendix D steps 3–4).
 *
 * @param rejectionReasons every hard rule broken, in check order; empty when accepted
 * @param signals device signals to store, hard and soft
 * @param isMockSuspected the server's conclusion that the location may be faked
 */
public record Verdict(List<RejectionReason> rejectionReasons, List<DetectedSignal> signals, boolean isMockSuspected) {

	public Verdict {
		rejectionReasons = List.copyOf(rejectionReasons);
		signals = List.copyOf(signals);
	}

	public boolean isAccepted() {
		return rejectionReasons.isEmpty();
	}

	/**
	 * The same verdict with one more rejection reason, for a rule checked after the
	 * anti-spoofing signals such as the renewal radius (FR-AR-04).
	 */
	public Verdict rejectedAlsoFor(RejectionReason reason) {
		List<RejectionReason> reasons = new ArrayList<>(rejectionReasons);
		reasons.add(reason);
		return new Verdict(reasons, signals, isMockSuspected);
	}

}
