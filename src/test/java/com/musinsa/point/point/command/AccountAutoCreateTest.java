package com.musinsa.point.point.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.musinsa.point.account.PointAccount;
import com.musinsa.point.account.PointAccountRepository;
import com.musinsa.point.support.AbstractPointIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AccountAutoCreateTest extends AbstractPointIntegrationTest {

    private static final String MEMBER_ID = "M8";

    @Autowired
    private EarnService earnService;

    @Autowired
    private PointAccountRepository accountRepository;

    @Test
    @DisplayName("첫 적립이 회원의 포인트 계정을 만들고 개인 한도는 비워 둔다")
    void firstEarningOpensTheAccount() {
        assertThat(accountRepository.findByMemberId(MEMBER_ID)).isEmpty();

        earnService.earn(MEMBER_ID, 1_000, null);

        PointAccount account = accountRepository.findByMemberId(MEMBER_ID).orElseThrow();
        assertThat(account.getMaxBalance()).isNull();
        assertThat(account.getCreatedAt()).isEqualTo(clock.instant());
    }

    @Test
    @DisplayName("같은 회원이 여러 번 적립해도 계정은 하나뿐이다")
    void repeatedEarningsReuseTheSameAccount() {
        earnService.earn(MEMBER_ID, 1_000, null);
        earnService.earn(MEMBER_ID, 2_000, null);
        earnService.earn(MEMBER_ID, 3_000, null);

        assertThat(accountRepository.count()).isEqualTo(1);
        assertThat(accountRepository.findByMemberId(MEMBER_ID)).isPresent();
    }

    @Test
    @DisplayName("회원마다 계정이 따로 생기고 잔액도 섞이지 않는다")
    void eachMemberGetsItsOwnAccount() {
        assertThat(earnService.earn("M8a", 1_000, null).balance()).isEqualTo(1_000);
        assertThat(earnService.earn("M8b", 2_000, null).balance()).isEqualTo(2_000);
        assertThat(earnService.earn("M8a", 500, null).balance()).isEqualTo(1_500);

        assertThat(accountRepository.count()).isEqualTo(2);
    }
}
