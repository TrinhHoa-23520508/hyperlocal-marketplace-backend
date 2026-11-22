package vn.edu.uit.nearby.nearby_backend.areaverification.application;

/**
 * Schedules of the area jobs (SRS appendix B "Công việc nền"). Times are Vietnamese local
 * time, the time the SRS uses for display (UTC+7, section 1.2).
 */
final class AreaJobSchedules {

	static final String VIETNAM_ZONE = "Asia/Ho_Chi_Minh";

	static final String DAILY_AT_09_00 = "0 0 9 * * *";

	static final String HOURLY = "0 0 * * * *";

	static final String DAILY_AT_02_00 = "0 0 2 * * *";

	static final int BATCH_SIZE = 500;

	private AreaJobSchedules() {
	}

}
