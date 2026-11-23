package vn.edu.uit.nearby.nearby_backend.shared.constant;

public final class ApiPaths {

	public static final String API_PATTERN = "/api/**";

	public static final String API_V1 = "/api/v1";

	public static final String AUTH_BASE = API_V1 + "/auth";

	public static final String AUTH_PATTERN = AUTH_BASE + "/**";

	public static final String CURRENT_USER_BASE = API_V1 + "/users/me";

	public static final String CURRENT_USER_PATTERN = CURRENT_USER_BASE + "/**";

	public static final String AREAS_BASE = API_V1 + "/areas";

	public static final String USERS_BASE = API_V1 + "/users";

	public static final String PUBLIC_PROFILE = USERS_BASE + "/{userId}";

	public static final String PUBLIC_USER_LISTINGS = PUBLIC_PROFILE + "/listings";

	public static final String CURRENT_USER_FAVORITES = CURRENT_USER_BASE + "/favorites";

	public static final String LISTINGS_BASE = API_V1 + "/listings";

	public static final String LISTING_IMAGES_BASE = LISTINGS_BASE + "/images";

	public static final String LISTING_DETAIL = LISTINGS_BASE + "/{listingId}";

	public static final String MY_LISTINGS = LISTINGS_BASE + "/me";

	public static final String POSTING_ELIGIBILITY = LISTINGS_BASE + "/posting-eligibility";

	public static final String CONVERSATIONS_BASE = API_V1 + "/conversations";

	public static final String CHAT_IMAGES_BASE = API_V1 + "/chat-images";

	public static final String NOTIFICATIONS_BASE = API_V1 + "/notifications";

	public static final String BADGES = CURRENT_USER_BASE + "/badges";

	public static final String NOTIFICATION_PREFERENCES = CURRENT_USER_BASE + "/notification-preferences";

	public static final String DEVICES_BASE = API_V1 + "/devices";

	public static final String CATEGORIES = API_V1 + "/categories";

	public static final String FEED = API_V1 + "/feed";

	public static final String SEARCH = API_V1 + "/search";

	public static final String MAP_LISTINGS = API_V1 + "/map/listings";

	public static final String ADMIN_BASE = API_V1 + "/admin";

	public static final String ADMIN_PATTERN = ADMIN_BASE + "/**";

	public static final String ADMIN_USERS_BASE = ADMIN_BASE + "/users";

	public static final String TEST_BASE = "/api/test";

	public static final String TEST_PATTERN = TEST_BASE + "/**";

	public static final String WEBSOCKET_ENDPOINT = "/ws";

	public static final String WEBSOCKET_PATTERN = WEBSOCKET_ENDPOINT + "/**";

	public static final String ERROR = "/error";

	public static final String ACTUATOR_PATTERN = "/actuator/**";

	public static final String ACTUATOR_HEALTH_PATTERN = "/actuator/health/**";

	public static final String ACTUATOR_INFO = "/actuator/info";

	public static final String ACTUATOR_PROMETHEUS = "/actuator/prometheus";

	public static final String OPENAPI_DOCS_PATTERN = "/v3/api-docs/**";

	public static final String OPENAPI_DOCS_YAML = "/v3/api-docs.yaml";

	public static final String SWAGGER_UI_PATTERN = "/swagger-ui/**";

	public static final String SWAGGER_UI_PAGE = "/swagger-ui.html";

	private ApiPaths() {
	}

}
