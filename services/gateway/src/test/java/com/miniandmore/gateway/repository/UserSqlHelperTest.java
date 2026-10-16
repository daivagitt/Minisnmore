package com.miniandmore.gateway.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.data.relational.core.sql.Table;

class UserSqlHelperTest {

    @Test
    void getColumns_returnsAllUserColumns() {
        assertThat(UserSqlHelper.getColumns(Table.create("jhi_user"), "u")).hasSize(13);
    }
}
