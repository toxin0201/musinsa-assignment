package com.musinsa.point.support;

import com.musinsa.point.common.PointKeyGenerator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 통합 테스트의 공통 바탕. 계정 생성처럼 별도 트랜잭션으로 커밋되는 쓰기가 있어 테스트 트랜잭션 롤백에 기대지 않고
 * 매 테스트 시작마다 표를 비운다.
 */
@SpringBootTest
@Import(TestDoubleConfig.class)
public abstract class AbstractPointIntegrationTest {

    private static final List<String> TABLES_IN_DELETION_ORDER = List.of(
            "point_transaction_detail", "point_earning", "point_transaction", "point_account");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected PointKeyGenerator pointKeyGenerator;

    @BeforeEach
    void resetWorld() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        TABLES_IN_DELETION_ORDER.forEach(table ->
                jdbcTemplate.execute("TRUNCATE TABLE " + table + " RESTART IDENTITY"));
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        clock.reset();
        ((SequentialPointKeyGenerator) pointKeyGenerator).reset();
    }
}
