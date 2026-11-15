package vn.edu.uit.nearby.nearby_backend.shared.openapi;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import vn.edu.uit.nearby.nearby_backend.support.IntegrationTest;

@IntegrationTest
class OpenApiDocumentIntegrationTests {

	private static final String LOGIN = "$.paths['/api/v1/auth/login'].post.responses";

	@Autowired
	MockMvc mvc;

	@Test
	void documentedErrorsCarryTheProblemDetailBodyAndTheirCodes() throws Exception {
		mvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath(LOGIN + "['429'].description", containsString("LOGIN_LOCKED")))
			.andExpect(jsonPath(LOGIN + "['429'].content['application/problem+json'].schema['$ref']")
				.value("#/components/schemas/Problem"))
			.andExpect(jsonPath(LOGIN + "['400'].description", containsString("VALIDATION_FAILED")))
			.andExpect(jsonPath("$.components.schemas.Problem.required", hasItems("code", "status")));
	}

	@Test
	void protectedEndpointsDocument401AndPublicOnesDoNot() throws Exception {
		mvc.perform(get("/v3/api-docs"))
			.andExpect(jsonPath("$.paths['/api/v1/users/me'].get.responses['401'].description",
					containsString("UNAUTHENTICATED")))
			.andExpect(jsonPath("$.paths['/api/v1/users/{userId}'].get.responses['401']").doesNotExist());
	}

	@Test
	void yamlVersionIsPublicForFrontendTooling() throws Exception {
		mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk());
	}

}
