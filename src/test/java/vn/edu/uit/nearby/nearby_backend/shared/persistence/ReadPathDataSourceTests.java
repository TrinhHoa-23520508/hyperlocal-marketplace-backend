package vn.edu.uit.nearby.nearby_backend.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.zaxxer.hikari.HikariDataSource;

class ReadPathDataSourceTests {

	private final DataSource primary = mock(DataSource.class);

	private final HikariDataSource replica = mock(HikariDataSource.class);

	private final Connection primaryConnection = mock(Connection.class);

	private final Connection replicaConnection = mock(Connection.class);

	private ReadPathDataSource dataSource;

	@BeforeEach
	void setUp() throws SQLException {
		given(primary.getConnection()).willReturn(primaryConnection);
		given(replica.getConnection()).willReturn(replicaConnection);
		given(primaryConnection.createStatement()).willReturn(mock(Statement.class));
		given(replicaConnection.createStatement()).willReturn(mock(Statement.class));
		dataSource = new ReadPathDataSource(primary, replica);
	}

	@Test
	@DisplayName("FR-DC-12: a read-only transaction runs on the read path")
	void readOnlyWorkRunsOnTheReplica() throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			connection.setReadOnly(true);
			connection.createStatement();
		}

		verify(replicaConnection).createStatement();
		verify(primaryConnection, never()).createStatement();
	}

	@Test
	void writesRunOnThePrimary() throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			connection.createStatement();
		}

		verify(primaryConnection).createStatement();
		verify(replica, never()).getConnection();
	}

	@Test
	@DisplayName("FR-DC-12: read path unavailable falls back to the primary")
	void readOnlyWorkFallsBackToThePrimaryWhenTheReplicaIsDown() throws SQLException {
		given(replica.getConnection()).willThrow(new SQLException("replica down"));

		try (Connection connection = dataSource.getConnection()) {
			connection.setReadOnly(true);
			connection.createStatement();
		}

		verify(primaryConnection).createStatement();
	}

	@Test
	void closingClosesTheReplicaPool() throws Exception {
		dataSource.close();

		verify(replica).close();
	}

	@Test
	void fallbackUsesTheGivenCredentialsOnBothDataSources() throws SQLException {
		DataSource preferred = mock(DataSource.class);
		given(preferred.getConnection("u", "p")).willThrow(new SQLException("down"));
		given(primary.getConnection("u", "p")).willReturn(primaryConnection);

		assertThat(new FallbackDataSource(preferred, primary).getConnection("u", "p")).isSameAs(primaryConnection);
	}

}
