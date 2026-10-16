package com.miniandmore.gateway.repository;

/**
 * One {@code jhi_user_authority} row, used when batch-loading roles for the admin user list.
 */
public record UserAuthorityAssignment(Long userId, String authorityName) {}
