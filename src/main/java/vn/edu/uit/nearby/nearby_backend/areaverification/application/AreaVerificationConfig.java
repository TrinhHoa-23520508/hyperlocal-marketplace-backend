package vn.edu.uit.nearby.nearby_backend.areaverification.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification.AreaVerificationPolicy;

@Configuration(proxyBeanMethods = false)
class AreaVerificationConfig {

	@Bean
	AreaVerificationPolicy areaVerificationPolicy() {
		return AreaVerificationPolicy.withSrsSignals();
	}

}
