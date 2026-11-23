package vn.edu.uit.nearby.nearby_backend.shared.persistence;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import com.zaxxer.hikari.HikariDataSource;

/**
 * The application data source when a read replica is configured: read-only transactions
 * ({@code @Transactional(readOnly = true)}) run on the replica, everything else on the
 * primary (FR-DC-12). The physical connection is picked at the first statement, after the
 * transaction manager has marked the connection read-only.
 */
class ReadPathDataSource extends LazyConnectionDataSourceProxy implements AutoCloseable {

	private final DataSource primary;

	private final HikariDataSource replica;

	ReadPathDataSource(DataSource primary, HikariDataSource replica) {
		super(primary);
		this.primary = primary;
		this.replica = replica;
		setReadOnlyDataSource(new FallbackDataSource(replica, primary));
	}

	/**
	 * Called by the container on shutdown in place of the primary pool's own close.
	 */
	@Override
	public void close() throws Exception {
		replica.close();
		if (primary instanceof AutoCloseable closeable) {
			closeable.close();
		}
	}

}
