package vn.edu.uit.nearby.nearby_backend.chat.notification.web;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import vn.edu.uit.nearby.nearby_backend.chat.notification.application.DeviceService;
import vn.edu.uit.nearby.nearby_backend.chat.notification.application.DeviceService.Registration;
import vn.edu.uit.nearby.nearby_backend.chat.notification.web.dto.DeviceResponse;
import vn.edu.uit.nearby.nearby_backend.chat.notification.web.dto.RegisterDeviceRequest;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ApiPaths;
import vn.edu.uit.nearby.nearby_backend.shared.security.CurrentUser;

@Tag(name = "Devices", description = "Register the FCM token of the app installation for push (FR-NT-02, FR-ID-03)")
@RestController
@RequestMapping(ApiPaths.DEVICES_BASE)
public class DeviceController {

	private final DeviceService devices;

	public DeviceController(DeviceService devices) {
		this.devices = devices;
	}

	@Operation(summary = "Save this installation's FCM token; call it after login and whenever FCM rotates the token",
			description = "deviceId is the installation id, the same one the app sends at login. One device has one token.")
	@PutMapping("/{deviceId}")
	@ApiResponse(responseCode = "400",
			description = "VALIDATION_FAILED: blank or too long id or token, unknown platform")
	public DeviceResponse registerDevice(@PathVariable String deviceId,
			@Valid @RequestBody RegisterDeviceRequest request) {
		devices.register(CurrentUser.requireId(),
				new Registration(deviceId, request.fcmToken(), request.platform(), request.appVersion()));
		return new DeviceResponse(deviceId.trim(), request.platform().trim().toUpperCase(Locale.ROOT));
	}

	@Operation(summary = "Forget this installation's token; nothing happens when it has none")
	@DeleteMapping("/{deviceId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unregisterDevice(@PathVariable String deviceId) {
		devices.unregister(CurrentUser.requireId(), deviceId.trim());
	}

}
