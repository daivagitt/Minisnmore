package com.miniandmore.gateway.repository;

import com.miniandmore.gateway.domain.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface UserRepository extends R2dbcRepository<User, Long> {

    @Query("SELECT * FROM jhi_user WHERE activation_key = :activationKey")
    Mono<User> findOneByActivationKey(String activationKey);

    Flux<User> findAllByActivatedIsFalseAndActivationKeyIsNotNullAndCreatedDateBefore(java.time.Instant dateTime);

    @Query("SELECT * FROM jhi_user WHERE reset_key = :resetKey")
    Mono<User> findOneByResetKey(String resetKey);

    @Query("SELECT * FROM jhi_user WHERE LOWER(email) = LOWER(:email)")
    Mono<User> findOneByEmailIgnoreCase(String email);

    @Query("SELECT * FROM jhi_user WHERE login = :login")
    Mono<User> findOneByLogin(String login);

    @Query("SELECT COUNT(*) FROM jhi_user")
    Mono<Long> count();

    @Query("SELECT COUNT(*) FROM jhi_user WHERE activated = true")
    Mono<Long> countActivatedUsers();

    @Query(
        """
        SELECT COUNT(DISTINCT u.id) FROM jhi_user u
        WHERE EXISTS (
          SELECT 1 FROM jhi_user_authority ua
          WHERE ua.user_id = u.id
            AND ua.authority_name IN ('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_SALES','ROLE_OPERATIONS','ROLE_CUSTOMER_SUPPORT')
        )
        """
    )
    Mono<Long> countStaffUsers();

    @Query("SELECT COUNT(DISTINCT user_id) FROM jhi_user_authority WHERE authority_name = :authority")
    Mono<Long> countUsersWithAuthority(String authority);

    @Query("SELECT * FROM jhi_user ORDER BY id LIMIT :limit OFFSET :offset")
    Flux<User> findAllByIdNotNull(Pageable pageable);

    @Query(
        """
        SELECT u.* FROM jhi_user u
        WHERE EXISTS (
          SELECT 1 FROM jhi_user_authority ua
          WHERE ua.user_id = u.id
            AND ua.authority_name IN ('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_SALES','ROLE_OPERATIONS','ROLE_CUSTOMER_SUPPORT')
        )
        ORDER BY u.id
        LIMIT :limit OFFSET :offset
        """
    )
    Flux<User> findAllStaffUsers(long limit, long offset);

    @Query("SELECT * FROM jhi_user ORDER BY created_date DESC NULLS LAST, id DESC LIMIT :limit OFFSET :offset")
    Flux<User> findAllUsersForAdmin(long limit, long offset);

    @Query(
        """
        SELECT COUNT(DISTINCT u.id) FROM jhi_user u
        WHERE NOT EXISTS (
          SELECT 1 FROM jhi_user_authority ua
          WHERE ua.user_id = u.id
            AND ua.authority_name IN ('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_SALES','ROLE_OPERATIONS','ROLE_CUSTOMER_SUPPORT')
        )
        """
    )
    Mono<Long> countCustomerUsers();

    @Query(
        """
        SELECT u.* FROM jhi_user u
        WHERE NOT EXISTS (
          SELECT 1 FROM jhi_user_authority ua
          WHERE ua.user_id = u.id
            AND ua.authority_name IN ('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_SALES','ROLE_OPERATIONS','ROLE_CUSTOMER_SUPPORT')
        )
        ORDER BY u.created_date DESC NULLS LAST, u.id DESC
        LIMIT :limit OFFSET :offset
        """
    )
    Flux<User> findAllCustomerUsers(long limit, long offset);

    @Query("SELECT * FROM jhi_user WHERE activated = true ORDER BY id LIMIT :limit OFFSET :offset")
    Flux<User> findAllActivatedUsers(long limit, long offset);

    @Query("DELETE FROM jhi_user WHERE activated = false AND login = :login")
    Mono<Void> deleteInactiveByLogin(String login);

    @Query("DELETE FROM jhi_user WHERE activated = false AND LOWER(email) = LOWER(:email)")
    Mono<Void> deleteInactiveByEmail(String email);

    /**
     * OAuth user lookup by email. Do not JOIN {@code jhi_user_authority}: multiple roles
     * (e.g. ROLE_ADMIN + ROLE_USER) would make {@code Mono} fail with non-unique result.
     * Load authorities separately via {@link UserAuthoritiesLoader}.
     */
    @Query("SELECT u.* FROM jhi_user u WHERE LOWER(u.email) = LOWER(:email)")
    Mono<User> findOneWithAuthoritiesByEmailIgnoreCaseForOAuth(String email);

    /** OAuth user lookup by login — same no-JOIN rule as email lookup above. */
    @Query("SELECT u.* FROM jhi_user u WHERE u.login = :login")
    Mono<User> findOneWithAuthoritiesByLoginForOAuth(String login);

    @Query("SELECT u.* FROM jhi_user u WHERE LOWER(u.email) = LOWER(:email) AND u.activated = true")
    Mono<User> findOneWithAuthoritiesByEmailIgnoreCase(String email);

    @Query("SELECT u.* FROM jhi_user u WHERE u.login = :login AND u.activated = true")
    Mono<User> findOneWithAuthoritiesByLogin(String login);

    @Query("SELECT password_hash FROM jhi_user WHERE id = :userId")
    Mono<String> findPasswordHashByUserId(Long userId);

    @Query("SELECT user_id, authority_name FROM jhi_user_authority WHERE user_id = ANY(:userIds)")
    Flux<UserAuthorityAssignment> findAuthorityNamesByUserIds(Long[] userIds);

    @Query("SELECT COALESCE(MAX(id), 0) FROM jhi_user")
    Mono<Long> findMaxId();

    /** Allocate the next primary key — {@code jhi_user.id} is not identity-backed in UAT. */
    @Query("SELECT nextval('jhi_user_id_seq')")
    Mono<Long> nextUserId();

    @Query(
        "INSERT INTO jhi_user_authority (user_id, authority_name) VALUES (:userId, :authority) ON CONFLICT DO NOTHING"
    )
    Mono<Void> saveUserAuthority(Long userId, String authority);

    @Query("DELETE FROM jhi_user_authority WHERE user_id = :userId")
    Mono<Void> deleteUserAuthorities(Long userId);

    @Query("DELETE FROM jhi_user_authority")
    Mono<Void> deleteAllUserAuthorities();
}
