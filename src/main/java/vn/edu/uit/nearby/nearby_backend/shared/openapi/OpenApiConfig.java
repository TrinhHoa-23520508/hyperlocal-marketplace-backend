package vn.edu.uit.nearby.nearby_backend.shared.openapi;

import java.util.List;
import java.util.Map;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import vn.edu.uit.nearby.nearby_backend.shared.constant.SecurityConstants;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

	private static final String PROBLEM_SCHEMA = "Problem";

	private static final String PROBLEM_JSON = org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;

	private static final int FIRST_ERROR_STATUS = 400;

	private static final String VALIDATION_FAILED = "VALIDATION_FAILED: errors lists each invalid field";

	@Bean
	OpenAPI nearbyOpenApi() {
		return new OpenAPI()
			.info(new Info().title("Nearby API")
				.version("v1")
				.description("Hyperlocal second-hand marketplace. Errors follow RFC 9457 (application/problem+json) "
						+ "with a stable `code`; lists use cursor-based pagination; state-changing calls accept an "
						+ "Idempotency-Key header. Business flows per screen: docs/api/<module>-api.md."))
			.components(new Components().addSecuritySchemes(SecurityConstants.OPENAPI_BEARER_SCHEME,
					new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
			.addSecurityItem(new SecurityRequirement().addList(SecurityConstants.OPENAPI_BEARER_SCHEME));
	}

	/**
	 * Gives every error response the problem-detail body, and documents the 400 and 401
	 * answers every endpoint can give, so generated clients can parse errors.
	 */
	@Bean
	OpenApiCustomizer problemDetailResponses() {
		return openApi -> {
			openApi.getComponents().addSchemas(PROBLEM_SCHEMA, problemSchema());
			openApi.getPaths().values().forEach(path -> path.readOperations().forEach(OpenApiConfig::documentErrors));
		};
	}

	private static void documentErrors(Operation operation) {
		ApiResponses responses = operation.getResponses();
		if (operation.getRequestBody() != null) {
			ApiResponse badRequest = responses.computeIfAbsent("400", status -> new ApiResponse());
			badRequest.setDescription(badRequest.getDescription() == null ? VALIDATION_FAILED
					: badRequest.getDescription() + " | " + VALIDATION_FAILED);
		}
		boolean isPublic = operation.getSecurity() != null && operation.getSecurity().isEmpty();
		if (!isPublic && !responses.containsKey("401")) {
			responses.addApiResponse("401", new ApiResponse().description("UNAUTHENTICATED: missing or expired token"));
		}
		responses.forEach((status, response) -> {
			if (isError(status)) {
				response.setContent(new Content().addMediaType(PROBLEM_JSON,
						new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_SCHEMA))));
			}
		});
	}

	private static boolean isError(String status) {
		return status.chars().allMatch(Character::isDigit) && Integer.parseInt(status) >= FIRST_ERROR_STATUS;
	}

	private static Schema<?> problemSchema() {
		Schema<?> fieldError = new ObjectSchema()
			.properties(Map.of("field", new StringSchema(), "message", new StringSchema()));
		return new ObjectSchema().required(List.of("status", "code"))
			.properties(Map.of("type", new StringSchema().example("https://nearby.uit.edu.vn/problems/otp-invalid"),
					"title", new StringSchema().example("Unprocessable Content"), "status",
					new IntegerSchema().example(422), "detail",
					new StringSchema().example("The code is wrong. 4 attempts left."), "code",
					new StringSchema().example("OTP_INVALID"), "errors",
					new ArraySchema().items(fieldError).description("Only for VALIDATION_FAILED"), "violations",
					new ArraySchema().items(new ObjectSchema()).description("Only for WEAK_PASSWORD")))
			.additionalProperties(Boolean.TRUE)
			.description("RFC 9457 problem detail. Branch on `code`, never on `detail`. Some codes add members: "
					+ "remainingAttempts (OTP_INVALID), reason and suspendedUntil (ACCOUNT_SUSPENDED / ACCOUNT_BANNED), "
					+ "nextAllowedAt (NAME_CHANGE_TOO_SOON), blockers (ACCOUNT_DELETION_BLOCKED).");
	}

}
