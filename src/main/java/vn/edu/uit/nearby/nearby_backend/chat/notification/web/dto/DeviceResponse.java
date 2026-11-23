package vn.edu.uit.nearby.nearby_backend.chat.notification.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The device whose token is now registered")
public record DeviceResponse(String deviceId, String platform) {

}
