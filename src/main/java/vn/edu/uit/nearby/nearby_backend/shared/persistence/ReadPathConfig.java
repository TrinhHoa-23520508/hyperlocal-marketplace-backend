package vn.edu.uit.nearby.nearby_backend.shared.persistence;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
class ReadPathConfig {

	@Bean
	static BeanPostProcessor readPathDataSourcePostProcessor(Environment environment) {
		return new ReadPathDataSourcePostProcessor(environment);
	}

}
