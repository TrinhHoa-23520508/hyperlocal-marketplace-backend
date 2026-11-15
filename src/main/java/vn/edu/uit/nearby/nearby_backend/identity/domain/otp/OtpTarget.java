package vn.edu.uit.nearby.nearby_backend.identity.domain.otp;

import vn.edu.uit.nearby.nearby_backend.identity.domain.PhoneNumber;

public record OtpTarget(PhoneNumber phone, OtpPurpose purpose) {

}
