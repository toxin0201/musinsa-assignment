package com.musinsa.point.point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.musinsa.point.account.PointAccount;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PointEarningTest {

    private static final Instant EARNED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-12-31T00:00:00Z");

    private PointEarning generalEarningOf(long amount) {
        PointAccount account = PointAccount.open("M1", EARNED_AT);
        PointTransaction transaction = PointTransaction.earn(account, "KEY-A", amount, EARNED_AT);
        return PointEarning.general(account, transaction, amount, EARNED_AT, EXPIRES_AT);
    }

    @Test
    @DisplayName("새 적립 건은 최초 적립액 전액을 잔액으로 갖고 활성 상태다")
    void newEarningStartsActiveWithFullRemaining() {
        PointEarning earning = generalEarningOf(1_000);

        assertThat(earning.getOriginalAmount()).isEqualTo(1_000);
        assertThat(earning.getRemainingAmount()).isEqualTo(1_000);
        assertThat(earning.getStatus()).isEqualTo(EarningStatus.ACTIVE);
        assertThat(earning.getKind()).isEqualTo(EarningKind.GENERAL);
        assertThat(earning.getAdminId()).isNull();
        assertThat(earning.getReason()).isNull();
    }

    @Test
    @DisplayName("관리자 수기 적립 건은 수기 종류와 관리자 정보를 갖는다")
    void manualEarningCarriesAdminInformation() {
        PointAccount account = PointAccount.open("M7", EARNED_AT);
        PointTransaction transaction = PointTransaction.earn(account, "KEY-M", 5_000, EARNED_AT);

        PointEarning earning = PointEarning.manual(
                account, transaction, 5_000, EARNED_AT, EXPIRES_AT, "admin01", "보상 지급");

        assertThat(earning.getKind()).isEqualTo(EarningKind.MANUAL);
        assertThat(earning.getAdminId()).isEqualTo("admin01");
        assertThat(earning.getReason()).isEqualTo("보상 지급");
    }

    @Test
    @DisplayName("차감한 만큼 잔액이 줄고 복원한 만큼 다시 늘어난다")
    void deductThenRestoreMovesRemainingAmount() {
        PointEarning earning = generalEarningOf(1_000);

        earning.deduct(300);
        assertThat(earning.getRemainingAmount()).isEqualTo(700);

        earning.restore(100);
        assertThat(earning.getRemainingAmount()).isEqualTo(800);
    }

    @Test
    @DisplayName("잔액보다 많이 차감하거나 최초 적립액을 넘겨 복원할 수 없다")
    void amountMovesStayWithinOriginalAmount() {
        PointEarning earning = generalEarningOf(1_000);
        earning.deduct(400);

        assertThatThrownBy(() -> earning.deduct(601)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> earning.restore(401)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> earning.deduct(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> earning.restore(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(earning.getRemainingAmount()).isEqualTo(600);
    }

    @Test
    @DisplayName("적립취소하면 취소 상태가 되고 잔액이 0 이 된다")
    void cancelClearsRemainingAmount() {
        PointEarning earning = generalEarningOf(1_000);

        earning.cancel();

        assertThat(earning.getStatus()).isEqualTo(EarningStatus.CANCELED);
        assertThat(earning.getRemainingAmount()).isZero();
    }

    @Test
    @DisplayName("이미 취소된 적립 건은 다시 취소할 수 없다")
    void canceledEarningCannotBeCanceledAgain() {
        PointEarning earning = generalEarningOf(1_000);
        earning.cancel();

        assertThatThrownBy(earning::cancel).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("만료 시각 이후에는 잔액이 남아 있어도 사용 대상이 아니다")
    void expiredEarningIsNotUsable() {
        PointEarning earning = generalEarningOf(1_000);

        assertThat(earning.isUsableAt(EXPIRES_AT.minusSeconds(1))).isTrue();
        assertThat(earning.isUsableAt(EXPIRES_AT)).isFalse();
        assertThat(earning.isUsableAt(EXPIRES_AT.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("취소됐거나 잔액이 0 인 적립 건은 사용 대상이 아니다")
    void canceledOrEmptyEarningIsNotUsable() {
        PointEarning emptied = generalEarningOf(1_000);
        emptied.deduct(1_000);
        assertThat(emptied.isUsableAt(EARNED_AT)).isFalse();

        PointEarning canceled = generalEarningOf(1_000);
        canceled.cancel();
        assertThat(canceled.isUsableAt(EARNED_AT)).isFalse();
    }
}
