package com.miniandmore.gateway.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.gateway.config.DatabaseConfiguration;
import com.miniandmore.gateway.domain.User;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.data.r2dbc.convert.MappingR2dbcConverter;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.data.r2dbc.mapping.R2dbcMappingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.sql.Table;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.core.ReactiveDataAccessStrategy;
import org.springframework.data.r2dbc.core.StatementMapper;
import org.springframework.data.r2dbc.query.UpdateMapper;
import org.springframework.data.relational.core.sql.Condition;
import org.springframework.data.relational.core.sql.OrderByField;
import org.springframework.data.relational.core.sql.Select;
import org.springframework.data.relational.core.sql.SelectBuilder.SelectFromAndJoin;
import org.springframework.data.relational.core.sql.SelectBuilder.SelectFromAndJoinCondition;
import org.springframework.data.relational.core.sql.SelectBuilder.SelectFromAndOrderBy;
import org.springframework.data.relational.core.sql.SelectBuilder.SelectWhereAndOr;
import org.springframework.data.relational.core.sql.render.SqlRenderer;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.FetchSpec;
import org.springframework.r2dbc.core.PreparedOperation;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class EntityManagerTest {

    private SqlRenderer sqlRenderer;
    private R2dbcEntityTemplate r2dbcEntityTemplate;
    private StatementMapper statementMapper;
    private DatabaseClient databaseClient;
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        sqlRenderer = mock(SqlRenderer.class);
        r2dbcEntityTemplate = mock(R2dbcEntityTemplate.class);
        statementMapper = mock(StatementMapper.class);
        databaseClient = mock(DatabaseClient.class);
        ReactiveDataAccessStrategy dataAccessStrategy = mock(ReactiveDataAccessStrategy.class);
        when(sqlRenderer.render(any(Select.class))).thenReturn("SELECT rendered");
        when(r2dbcEntityTemplate.getDataAccessStrategy()).thenReturn(dataAccessStrategy);
        when(dataAccessStrategy.getStatementMapper()).thenReturn(statementMapper);
        when(r2dbcEntityTemplate.getDatabaseClient()).thenReturn(databaseClient);
        entityManager = new EntityManager(sqlRenderer, mock(UpdateMapper.class), r2dbcEntityTemplate);
    }

    @Test
    void linkTable_requiresNonNullArguments() {
        assertThatThrownBy(() -> new EntityManager.LinkTable(null, "id", "ref")).isInstanceOf(IllegalArgumentException.class);
        assertThat(new EntityManager.LinkTable("link", "id", "ref")).isNotNull();
    }

    @Test
    void camelCaseToSnakeCase_convertsNames() {
        assertThat(EntityManager.camelCaseToSnakeCase("firstName")).isEqualTo("first_name");
        assertThat(EntityManager.camelCaseToSnakeCase("orderItem")).isEqualTo("order_item");
    }

    @Test
    void createOrderByField_supportsDotNotation() {
        OrderByField field = ReflectionTestUtils.invokeMethod(EntityManager.class, "createOrderByField", "userTable.email");
        assertThat(field).isNotNull();
    }

    @Test
    void createOrderByFields_supportsAscendingAndDotNotation() {
        Table table = Table.create("jhi_user").as(EntityManager.ENTITY_ALIAS);
        @SuppressWarnings("unchecked")
        Collection<OrderByField> fields = ReflectionTestUtils.invokeMethod(
            EntityManager.class,
            "createOrderByFields",
            table,
            Sort.by(Sort.Order.asc("login"), Sort.Order.desc("userTable.email"))
        );
        assertThat(fields).hasSize(2);
    }

    @Test
    void createSelect_withPageableAndWhere() {
        SelectFromAndJoin selectFrom = mock(SelectFromAndJoin.class);
        SelectFromAndJoin limited = mock(SelectFromAndJoin.class);
        SelectWhereAndOr whereAfterLimit = mock(SelectWhereAndOr.class);
        Select select = mock(Select.class);
        Condition where = mock(Condition.class);
        when(selectFrom.limitOffset(10, 0)).thenReturn(limited);
        when(limited.where(where)).thenReturn(whereAfterLimit);
        when(whereAfterLimit.build()).thenReturn(select);

        assertThat(entityManager.createSelect(selectFrom, User.class, PageRequest.of(0, 10), where)).isEqualTo("SELECT rendered");
        verify(sqlRenderer).render(select);
    }

    @Test
    void createSelect_withPageableWithoutWhere() {
        SelectFromAndJoin selectFrom = mock(SelectFromAndJoin.class);
        SelectFromAndJoin limited = mock(SelectFromAndJoin.class);
        Select select = mock(Select.class);
        when(selectFrom.limitOffset(10, 0)).thenReturn(limited);
        when(limited.build()).thenReturn(select);

        assertThat(entityManager.createSelect(selectFrom, User.class, PageRequest.of(0, 10), null)).isEqualTo("SELECT rendered");
    }

    @Test
    void createSelect_withoutPageableWithWhere() {
        SelectFromAndJoin selectFrom = mock(SelectFromAndJoin.class);
        Select select = mock(Select.class);
        Condition where = mock(Condition.class);
        SelectWhereAndOr whereSpec = mock(SelectWhereAndOr.class);
        when(selectFrom.where(where)).thenReturn(whereSpec);
        when(whereSpec.build()).thenReturn(select);

        assertThat(entityManager.createSelect(selectFrom, User.class, null, where)).isEqualTo("SELECT rendered");
    }

    @Test
    void createSelect_withoutPageableOrWhere() {
        SelectFromAndJoin selectFrom = mock(SelectFromAndJoin.class);
        Select select = mock(Select.class);
        when(selectFrom.build()).thenReturn(select);

        assertThat(entityManager.createSelect(selectFrom, User.class, null, null)).isEqualTo("SELECT rendered");
    }

    @Test
    void createSelect_fromJoinCondition_withPageable() {
        SelectFromAndJoinCondition selectFrom = mock(SelectFromAndJoinCondition.class);
        Select select = mock(Select.class);
        SelectFromAndJoin join = mock(SelectFromAndJoin.class);
        when(selectFrom.limitOffset(5, 5)).thenReturn(join);
        when(join.build()).thenReturn(select);

        assertThat(entityManager.createSelect(selectFrom, User.class, PageRequest.of(1, 5), null)).isEqualTo("SELECT rendered");
    }

    @Test
    void createSelect_fromJoinCondition_withPageableAndWhere() {
        SelectFromAndJoinCondition selectFrom = mock(SelectFromAndJoinCondition.class);
        SelectFromAndJoin join = mock(SelectFromAndJoin.class);
        SelectWhereAndOr whereSpec = mock(SelectWhereAndOr.class);
        Select select = mock(Select.class);
        Condition where = mock(Condition.class);
        when(selectFrom.limitOffset(5, 5)).thenReturn(join);
        when(join.where(where)).thenReturn(whereSpec);
        when(whereSpec.build()).thenReturn(select);

        assertThat(entityManager.createSelect(selectFrom, User.class, PageRequest.of(1, 5), where)).isEqualTo("SELECT rendered");
    }

    @Test
    void createSelect_fromJoinCondition_withWhere() {
        SelectFromAndJoinCondition selectFrom = mock(SelectFromAndJoinCondition.class);
        Select select = mock(Select.class);
        Condition where = mock(Condition.class);
        SelectWhereAndOr whereSpec = mock(SelectWhereAndOr.class);
        when(selectFrom.where(where)).thenReturn(whereSpec);
        when(whereSpec.build()).thenReturn(select);

        assertThat(entityManager.createSelect(selectFrom, User.class, null, where)).isEqualTo("SELECT rendered");
    }

    @Test
    void createSelect_rendersBuiltSelect() {
        Select select = mock(Select.class);
        assertThat(entityManager.createSelect(select)).isEqualTo("SELECT rendered");
    }

    @Test
    void deleteAllByEntityType() {
        org.springframework.data.r2dbc.core.ReactiveDeleteOperation.ReactiveDelete delete = mock(
            org.springframework.data.r2dbc.core.ReactiveDeleteOperation.ReactiveDelete.class
        );
        when(r2dbcEntityTemplate.delete(User.class)).thenReturn(delete);
        when(delete.all()).thenReturn(Mono.just(1L));

        StepVerifier.create(entityManager.deleteAll(User.class)).expectNext(1L).verifyComplete();
    }

    @Test
    void deleteAllByTableName() {
        StatementMapper.DeleteSpec deleteSpec = mock(StatementMapper.DeleteSpec.class);
        DatabaseClient.GenericExecuteSpec executeSpec = mock(DatabaseClient.GenericExecuteSpec.class);
        @SuppressWarnings("unchecked")
        FetchSpec<Map<String, Object>> fetchSpec = mock(FetchSpec.class);
        PreparedOperation<?> prepared = preparedOperation();
        when(statementMapper.createDelete("jhi_link")).thenReturn(deleteSpec);
        when(statementMapper.getMappedObject(deleteSpec)).thenAnswer(invocation -> prepared);
        when(databaseClient.sql(isA(PreparedOperation.class))).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(3L));

        StepVerifier.create(entityManager.deleteAll("jhi_link")).expectNext(3L).verifyComplete();
    }

    @Test
    void insert_delegatesToTemplate() {
        User user = new User();
        when(r2dbcEntityTemplate.insert(user)).thenReturn(Mono.just(user));
        StepVerifier.create(entityManager.insert(user)).expectNext(user).verifyComplete();
    }

    @Test
    void deleteFromLinkTable_rejectsNullEntityId() {
        EntityManager.LinkTable linkTable = new EntityManager.LinkTable("jhi_user_authority", "user_id", "authority_name");
        assertThatThrownBy(() -> entityManager.deleteFromLinkTable(linkTable, null).block()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteFromLinkTable_executesDelete() {
        EntityManager.LinkTable linkTable = new EntityManager.LinkTable("jhi_user_authority", "user_id", "authority_name");
        StatementMapper.DeleteSpec deleteSpec = mock(StatementMapper.DeleteSpec.class);
        DatabaseClient.GenericExecuteSpec executeSpec = mock(DatabaseClient.GenericExecuteSpec.class);
        PreparedOperation<?> prepared = preparedOperation();
        when(statementMapper.createDelete("jhi_user_authority")).thenReturn(deleteSpec);
        when(deleteSpec.withCriteria(any())).thenReturn(deleteSpec);
        when(statementMapper.getMappedObject(deleteSpec)).thenAnswer(invocation -> prepared);
        when(databaseClient.sql(isA(PreparedOperation.class))).thenReturn(executeSpec);
        when(executeSpec.then()).thenReturn(Mono.empty());

        StepVerifier.create(entityManager.deleteFromLinkTable(linkTable, 42L)).verifyComplete();
    }

    @Test
    void createSelect_fromJoinCondition_withoutPageableOrWhere() {
        SelectFromAndJoinCondition selectFrom = mock(SelectFromAndJoinCondition.class);
        Select select = mock(Select.class);
        when(selectFrom.build()).thenReturn(select);

        assertThat(entityManager.createSelect(selectFrom, User.class, null, null)).isEqualTo("SELECT rendered");
    }

    @Test
    void updateLinkTable_replacesReferences() {
        EntityManager.LinkTable linkTable = new EntityManager.LinkTable("jhi_user_authority", "user_id", "authority_name");
        StatementMapper.DeleteSpec deleteSpec = mock(StatementMapper.DeleteSpec.class);
        StatementMapper.InsertSpec insertSpec = mock(StatementMapper.InsertSpec.class);
        DatabaseClient.GenericExecuteSpec executeSpec = mock(DatabaseClient.GenericExecuteSpec.class);
        @SuppressWarnings("unchecked")
        FetchSpec<Map<String, Object>> fetchSpec = mock(FetchSpec.class);
        PreparedOperation<?> prepared = preparedOperation();
        when(statementMapper.createDelete(anyString())).thenReturn(deleteSpec);
        when(statementMapper.createInsert(anyString())).thenReturn(insertSpec);
        when(insertSpec.withColumn(anyString(), any())).thenReturn(insertSpec);
        when(deleteSpec.withCriteria(any())).thenReturn(deleteSpec);
        when(statementMapper.getMappedObject(deleteSpec)).thenAnswer(invocation -> prepared);
        when(statementMapper.getMappedObject(insertSpec)).thenAnswer(invocation -> prepared);
        when(databaseClient.sql(isA(PreparedOperation.class))).thenReturn(executeSpec);
        when(executeSpec.then()).thenReturn(Mono.empty());
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier
            .create(entityManager.updateLinkTable(linkTable, 1L, Stream.of("ROLE_USER", "ROLE_ADMIN")))
            .expectNext(2L)
            .verifyComplete();
    }

    @SuppressWarnings("unchecked")
    private static PreparedOperation<?> preparedOperation() {
        return mock(PreparedOperation.class);
    }
}
