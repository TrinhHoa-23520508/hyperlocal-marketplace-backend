package vn.edu.uit.nearby.nearby_backend.shared.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

import vn.edu.uit.nearby.nearby_backend.shared.constant.ApiPaths;
import vn.edu.uit.nearby.nearby_backend.shared.constant.HeaderNames;
import vn.edu.uit.nearby.nearby_backend.shared.constant.Roles;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

	private static final String[] PUBLIC_ENDPOINTS = { ApiPaths.AUTH_PATTERN, ApiPaths.TEST_PATTERN,
			ApiPaths.WEBSOCKET_PATTERN, ApiPaths.ERROR, ApiPaths.ACTUATOR_HEALTH_PATTERN, ApiPaths.ACTUATOR_INFO,
			ApiPaths.ACTUATOR_PROMETHEUS, ApiPaths.OPENAPI_DOCS_PATTERN, ApiPaths.OPENAPI_DOCS_YAML,
			ApiPaths.SWAGGER_UI_PATTERN, ApiPaths.SWAGGER_UI_PAGE };

	private static final String[] ADMIN_ENDPOINTS = { ApiPaths.ADMIN_PATTERN, ApiPaths.ACTUATOR_PATTERN };

	/**
	 * Reads guests may make (FR-DC-01..08: actors "Khách, Người dùng"). A token, when
	 * sent, still identifies the user.
	 */
	private static final String[] PUBLIC_READS = { ApiPaths.PUBLIC_PROFILE, ApiPaths.PUBLIC_USER_LISTINGS,
			ApiPaths.CATEGORIES, ApiPaths.FEED, ApiPaths.SEARCH, ApiPaths.MAP_LISTINGS, ApiPaths.LISTING_DETAIL };

	/**
	 * Signed-in reads under {@code /listings} whose paths would otherwise match the
	 * public listing detail.
	 */
	private static final String[] OWNER_LISTING_READS = { ApiPaths.MY_LISTINGS, ApiPaths.POSTING_ELIGIBILITY,
			ApiPaths.LISTING_IMAGES_BASE + "/**" };

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
		AuthenticationEntryPoint entryPoint = (request, response, ex) -> exceptionResolver.resolveException(request,
				response, null, ex);
		AccessDeniedHandler accessDeniedHandler = (request, response, ex) -> exceptionResolver.resolveException(request,
				response, null, ex);
		return http.csrf(AbstractHttpConfigurer::disable)
			.cors(Customizer.withDefaults())
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth.requestMatchers(PUBLIC_ENDPOINTS)
				.permitAll()
				.requestMatchers(ApiPaths.CURRENT_USER_PATTERN)
				.authenticated()
				.requestMatchers(HttpMethod.GET, OWNER_LISTING_READS)
				.authenticated()
				.requestMatchers(HttpMethod.GET, PUBLIC_READS)
				.permitAll()
				.requestMatchers(ADMIN_ENDPOINTS)
				.hasRole(Roles.ADMIN)
				.anyRequest()
				.authenticated())
			.oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
				.authenticationEntryPoint(entryPoint)
				.accessDeniedHandler(accessDeniedHandler))
			.exceptionHandling(
					handling -> handling.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
			.build();
	}

	/**
	 * Tokens travel in the Authorization header, never in cookies, so credentials stay
	 * off.
	 */
	@Bean
	CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
		CorsConfiguration cors = new CorsConfiguration();
		cors.setAllowedOrigins(properties.allowedOrigins());
		cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, HeaderNames.IDEMPOTENCY_KEY,
				HttpHeaders.ACCEPT_LANGUAGE));
		cors.setExposedHeaders(List.of(HttpHeaders.RETRY_AFTER, HttpHeaders.LOCATION));
		cors.setAllowCredentials(false);
		cors.setMaxAge(properties.maxAge());
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration(ApiPaths.API_PATTERN, cors);
		return source;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

}
