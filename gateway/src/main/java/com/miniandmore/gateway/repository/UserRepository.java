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

    @Query("SELECT * FROM jhi_user WHERE activated = false AND login = :login")
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

    @Query("SELECT * FROM jhi_user ORDER BY id LIMIT :limit OFFSET :offset")
    Flux<User> findAllByIdNotNull(Pageable pageable);

    @Query("SELECT * FROM jhi_user WHERE activated = true ORDER BY id LIMIT :limit OFFSET :offset")
    Flux<User> findAllByActivatedIsTrue(Pageable pageable);

    @Query("DELETE FROM jhi_user WHERE activated = false AND login = :login")
    Mono<Void> deleteInactiveByLogin(String login);

    @Query("DELETE FROM jhi_user WHERE activated = false AND LOWER(email) = LOWER(:email)")
    Mono<Void> deleteInactiveByEmail(String email);

    @Query(
        "SELECT u.* FROM jhi_user u " +
        "LEFT JOIN jhi_user_authority ua ON u.id = ua.user_id " +
        "WHERE LOWER(u.email) = LOWER(:email)"
    )
    Mono<User> findOneWithAuthoritiesByEmailIgnoreCaseForOAuth(String email);

    @Query(
        "SELECT u.* FROM jhi_user u " +
        "LEFT JOIN jhi_user_authority ua ON u.id = ua.user_id " +
        "WHERE u.login = :login"
    )
    Mono<User> findOneWithAuthoritiesByLoginForOAuth(String login);

    @Query("SELECT u.* FROM jhi_user u WHERE LOWER(u.email) = LOWER(:email) AND u.activated = true")
    Mono<User> findOneWithAuthoritiesByEmailIgnoreCase(String email);

    @Query("SELECT u.* FROM jhi_user u WHERE u.login = :login AND u.activated = true")
    Mono<User> findOneWithAuthoritiesByLogin(String login);

    @Query("SELECT password_hash FROM jhi_user WHERE id = :userId")
    Mono<String> findPasswordHashByUserId(Long userId);

    @Query("SELECT authority_name FROM jhi_user_authority WHERE user_id = :userId")
    reactor.core.publisher.Flux<String> findAuthorityNamesByUserId(Long userId);
}
