package vn.edu.uit.nearby.nearby_backend.shared.persistence;

import javax.sql.DataSource;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Wraps the auto-configured primary data source in a {@link ReadPathDataSource} when
 * {@code app.datasource.read.url} is set, so adding a replica needs no code change
 * (FR-DC-12). Without it the primary data source is left as it is.
 */
class ReadPathDataSourcePostProcessor implements BeanPostProcessor {

	static final String PRIMARY_BEAN_NAME = "dataSource";

	private static final String REPLICA_POOL_NAME = "read-path";

	/**
	 * Lets the application start while the replica is down; connections are made on
	 * demand and fall back to the primary.
	 */
	private static final long NO_INITIAL_CONNECTION = -1;

	private final Environment environment;

	ReadPathDataSourcePostProcessor(Environment environment) {
		this.environment = environment;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {
		if (!PRIMARY_BEAN_NAME.equals(beanName) || !(bean instanceof DataSource primary)
				|| bean instanceof ReadPathDataSource) {
			return bean;
		}
		ReadPathProperties properties = Binder.get(environment)
			.bind(ReadPathProperties.PREFIX, ReadPathProperties.class)
			.orElse(null);
		if (properties == null || !properties.isConfigured()) {
			return bean;
		}
		return new ReadPathDataSource(primary, replicaPool(properties));
	}

	private static HikariDataSource replicaPool(ReadPathProperties properties) {
		HikariConfig config = new HikariConfig();
		config.setPoolName(REPLICA_POOL_NAME);
		config.setJdbcUrl(properties.url());
		config.setUsername(properties.username());
		config.setPassword(properties.password());
		config.setMaximumPoolSize(properties.maximumPoolSize());
		config.setConnectionTimeout(properties.connectionTimeout().toMillis());
		config.setInitializationFailTimeout(NO_INITIAL_CONNECTION);
		config.setReadOnly(true);
		return new HikariDataSource(config);
	}

}
