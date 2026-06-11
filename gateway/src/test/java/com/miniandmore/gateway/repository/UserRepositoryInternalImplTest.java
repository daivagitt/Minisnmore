package com.miniandmore.gateway.repository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.miniandmore.gateway.domain.Authority;
import com.miniandmore.gateway.domain.User;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import java.util.function.BiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.core.ReactiveDeleteOperation;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.RowsFetchSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserRepositoryInternalImplTest {

    @Mock
    private DatabaseClient db;

    @Mock
    private R2dbcEntityTemplate r2dbcEntityTemplate;

    @Mock
    private R2dbcConverter r2dbcConverter;

    private UserRepositoryInternalImpl repository;

    @BeforeEach
    void setUp() {
        repository = new UserRepositoryInternalImpl(db, r2dbcEntityTemplate, r2dbcConverter);
    }

    @Test
    void findOneWithAuthoritiesByLogin_returnsUserWithAuthorities() {
        stubSqlQueryWithTuples(Tuples.of(user("login"), java.util.Optional.of("ROLE_USER")));

        StepVerifier
            .create(repository.findOneWithAuthoritiesByLogin("login"))
            .assertNext(user -> {
                org.assertj.core.api.Assertions.assertThat(user.getLogin()).isEqualTo("login");
                org.assertj.core.api.Assertions.assertThat(user.getAuthorities()).extracting(Authority::getName).containsExactly("ROLE_USER");
                org.assertj.core.api.Assertions.assertThat(user.isNew()).isFalse();
            })
            .verifyComplete();
    }

    @Test
    void findOneWithAuthoritiesByEmailIgnoreCase_returnsEmptyWhenNoRows() {
        stubSqlQueryWithTuples();

        StepVerifier.create(repository.findOneWithAuthoritiesByEmailIgnoreCase("MISSING@localhost")).verifyComplete();
    }

    @Test
    void findAllWithAuthorities_pagesAndSorts() {
        User user1 = user("b");
        User user2 = user("a");
        stubSqlQueryWithTuples(
            Tuples.of(user1, java.util.Optional.of("ROLE_USER")),
            Tuples.of(user2, java.util.Optional.empty())
        );

        StepVerifier
            .create(repository.findAllWithAuthorities(PageRequest.of(0, 1, Sort.by("login"))))
            .expectNextCount(1)
            .verifyComplete();
    }

    @Test
    void findAllWithAuthorities_sortsDescending() {
        stubSqlQueryWithTuples(Tuples.of(user("a"), java.util.Optional.empty()), Tuples.of(user("b"), java.util.Optional.empty()));

        StepVerifier
            .create(repository.findAllWithAuthorities(PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "login"))))
            .expectNextCount(2)
            .verifyComplete();
    }

    @Test
    void delete_removesAuthoritiesThenUser() {
        DatabaseClient.GenericExecuteSpec executeSpec = mock(DatabaseClient.GenericExecuteSpec.class);
        when(db.sql(anyString())).thenReturn(executeSpec);
        lenient().when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.then()).thenReturn(Mono.empty());

        ReactiveDeleteOperation.ReactiveDelete delete = mock(ReactiveDeleteOperation.ReactiveDelete.class);
        ReactiveDeleteOperation.TerminatingDelete terminatingDelete = mock(ReactiveDeleteOperation.TerminatingDelete.class);
        when(r2dbcEntityTemplate.delete(User.class)).thenReturn(delete);
        when(delete.matching(any())).thenReturn(terminatingDelete);
        when(terminatingDelete.all()).thenReturn(Mono.just(1L));

        User user = new User();
        user.setId(42L);

        StepVerifier.create(repository.delete(user)).verifyComplete();
    }

    private User user(String login) {
        User user = new User();
        user.setId(1L);
        user.setLogin(login);
        user.setPassword("x".repeat(60));
        user.setActivated(true);
        user.setCreatedBy("system");
        return user;
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    private void stubSqlQueryWithTuples(Tuple2<User, java.util.Optional<String>>... tuples) {
        DatabaseClient.GenericExecuteSpec executeSpec = mock(DatabaseClient.GenericExecuteSpec.class);
        RowsFetchSpec<Tuple2<User, java.util.Optional<String>>> fetchSpec = mock(RowsFetchSpec.class);
        io.r2dbc.spi.RowMetadata metadata = mock(io.r2dbc.spi.RowMetadata.class);

        when(db.sql(anyString())).thenReturn(executeSpec);
        lenient().when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenAnswer(invocation -> {
            BiFunction<Row, io.r2dbc.spi.RowMetadata, Tuple2<User, java.util.Optional<String>>> mapper = invocation.getArgument(0);
            Flux<Tuple2<User, java.util.Optional<String>>> flux = Flux.fromArray(tuples).map(t -> {
                Row row = mock(Row.class);
                User mappedUser = t.getT1();
                when(row.get("id", Long.class)).thenReturn(mappedUser.getId());
                when(row.get("login")).thenReturn(mappedUser.getLogin());
                lenient().when(row.get("password_hash")).thenReturn(mappedUser.getPassword());
                when(row.get("first_name")).thenReturn(mappedUser.getFirstName());
                when(row.get("last_name")).thenReturn(mappedUser.getLastName());
                when(row.get("email")).thenReturn(mappedUser.getEmail());
                when(row.get("image_url")).thenReturn(mappedUser.getImageUrl());
                when(row.get("activated")).thenReturn(mappedUser.isActivated());
                when(row.get("lang_key")).thenReturn(mappedUser.getLangKey());
                when(row.get("activation_key")).thenReturn(mappedUser.getActivationKey());
                when(row.get("reset_key")).thenReturn(mappedUser.getResetKey());
                when(row.get("reset_date")).thenReturn(mappedUser.getResetDate());
                when(row.get("created_by")).thenReturn(mappedUser.getCreatedBy());
                when(row.get("created_date")).thenReturn(mappedUser.getCreatedDate());
                when(row.get("last_modified_by")).thenReturn(mappedUser.getLastModifiedBy());
                when(row.get("last_modified_date")).thenReturn(mappedUser.getLastModifiedDate());
                when(row.get("authority_name")).thenReturn(t.getT2().orElse(null));
                return mapper.apply(row, metadata);
            });
            when(fetchSpec.all()).thenReturn(flux);
            return fetchSpec;
        });
    }
}
