package com.truyengg.model.response;

public record DashboardStatsResponse(
    Long totalUsers,
    Long totalAdmins,
    Long totalStories,
    Long totalReports
) {
}
