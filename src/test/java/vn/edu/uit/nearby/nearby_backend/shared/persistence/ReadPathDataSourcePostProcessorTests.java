package vn.edu.uit.nearby.nearby_backend.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ReadPathDataSourcePostProcessorTests {

	private final DataSource primary = mock(DataSource.class);

	@Test
	void keepsThePrimaryWhenNoReplicaIsConfigured() {
		Object bean = new ReadPathDataSourcePostProcessor(new MockEnvironment()).postProcessAfterInitialization(primary,
				ReadPathDataSourcePostProcessor.PRIMARY_BEAN_NAME);

		assertThat(bean).isSameAs(primary);
	}

	@Test
	void keepsThePrimaryWhenTheReplicaUrlIsEmpty() {
		MockEnvironment environment = new MockEnvironment().withProperty("app.datasource.read.url", "");

		Object bean = new ReadPathDataSourcePostProcessor(environment).postProcessAfterInitialization(primary,
				ReadPathDataSourcePostProcessor.PRIMARY_BEAN_NAME);

		assertThat(bean).isSameAs(primary);
	}

	@Test
	void routesReadOnlyWorkWhenAReplicaIsConfigured() throws Exception {
		MockEnvironment environment = new MockEnvironment()
			.withProperty("app.datasource.read.url", "jdbc:postgresql://replica.invalid:5432/nearby")
			.withProperty("app.datasource.read.username", "nearby");

		Object bean = new ReadPathDataSourcePostProcessor(environment).postProcessAfterInitialization(primary,
				ReadPathDataSourcePostProcessor.PRIMARY_BEAN_NAME);

		assertThat(bean).isInstanceOf(ReadPathDataSource.class);
		((ReadPathDataSource) bean).close();
	}

	@Test
	void leavesOtherBeansAlone() {
		MockEnvironment environment = new MockEnvironment().withProperty("app.datasource.read.url",
				"jdbc:postgresql://replica.invalid:5432/nearby");

		Object bean = new ReadPathDataSourcePostProcessor(environment).postProcessAfterInitialization(primary,
				"reportingDataSource");

		assertThat(bean).isSameAs(primary);
	}

}
