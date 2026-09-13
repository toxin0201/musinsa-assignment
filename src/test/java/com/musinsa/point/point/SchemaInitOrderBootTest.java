package com.musinsa.point.point;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * 스키마 DDL 은 schema.sql 한 곳에서만 관리하고 Hibernate 는 검증만 한다.
 * 이 테스트가 기동된다는 사실 자체가 "schema.sql 이 Hibernate validate 보다 먼저 실행됐다"는 증거다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SchemaInitOrderBootTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("기동 시 포인트 테이블 4개가 DDL 스크립트로 준비되고 엔티티 매핑 검증을 통과한다")
    void schemaScriptRunsBeforeMappingValidation() {
        @SuppressWarnings("unchecked")
        List<String> tables = entityManager
                .createNativeQuery("select table_name from information_schema.tables where table_schema = 'PUBLIC'")
                .getResultList();

        assertThat(tables).contains(
                "POINT_ACCOUNT", "POINT_EARNING", "POINT_TRANSACTION", "POINT_TRANSACTION_DETAIL");
    }

    @Test
    @DisplayName("적립 건 테이블에 설계가 정한 컬럼이 모두 있다")
    void earningTableHasDesignedColumns() {
        @SuppressWarnings("unchecked")
        List<String> columns = entityManager
                .createNativeQuery("select column_name from information_schema.columns"
                        + " where table_schema = 'PUBLIC' and table_name = 'POINT_EARNING'")
                .getResultList();

        assertThat(columns).contains(
                "ID", "ACCOUNT_ID", "TRANSACTION_ID", "KIND", "ORIGINAL_AMOUNT", "REMAINING_AMOUNT",
                "EARNED_AT", "EXPIRES_AT", "STATUS", "ADMIN_ID", "REASON", "REISSUED_FROM_TRANSACTION_ID");
    }

    @Test
    @DisplayName("사용 거래의 주문번호는 계정 안에서 한 번만 존재하도록 유일 제약이 걸려 있다")
    void useOrderNoHasUniqueConstraint() {
        @SuppressWarnings("unchecked")
        List<String> uniqueColumns = entityManager
                .createNativeQuery("select column_name from information_schema.key_column_usage"
                        + " where table_schema = 'PUBLIC' and table_name = 'POINT_TRANSACTION'"
                        + " and constraint_name = 'UK_POINT_TRANSACTION_USE_ORDER'")
                .getResultList();

        assertThat(uniqueColumns).containsExactlyInAnyOrder("ACCOUNT_ID", "USE_ORDER_NO");
    }
}
