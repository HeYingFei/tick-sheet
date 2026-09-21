package com.todo.support;

import com.todo.common.BizException;
import com.todo.common.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 领域规则单元测试，对应设计方案 §9.2 的关键验收用例。
 * 纯逻辑，不依赖数据库与 Spring 上下文。
 */
class DomainRulesTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 9, 17, 12, 0, 0, 0, ZoneOffset.ofHours(8));

    @Nested
    @DisplayName("§4.2 逾期判定")
    class OverdueRules {

        @Test
        @DisplayName("AC-03 待办任务截止时间已过 → 逾期，状态不变")
        void pendingTaskPastDueIsOverdue() {
            OffsetDateTime yesterday = NOW.minusDays(1);
            assertThat(OverdueCalculator.isOverdue(TaskStatus.TODO, yesterday, NOW)).isTrue();
            assertThat(OverdueCalculator.isOverdue(TaskStatus.DOING, yesterday, NOW)).isTrue();
        }

        @Test
        @DisplayName("未到截止时间不算逾期")
        void futureDueIsNotOverdue() {
            assertThat(OverdueCalculator.isOverdue(TaskStatus.TODO, NOW.plusDays(1), NOW)).isFalse();
        }

        @Test
        @DisplayName("已完成、已取消的任务不显示为逾期")
        void finishedTasksAreNotOverdue() {
            OffsetDateTime yesterday = NOW.minusDays(1);
            assertThat(OverdueCalculator.isOverdue(TaskStatus.DONE, yesterday, NOW)).isFalse();
            assertThat(OverdueCalculator.isOverdue(TaskStatus.CANCELED, yesterday, NOW)).isFalse();
        }

        @Test
        @DisplayName("无截止时间不算逾期")
        void nullDueIsNotOverdue() {
            assertThat(OverdueCalculator.isOverdue(TaskStatus.TODO, null, NOW)).isFalse();
        }

        @Test
        @DisplayName("AC-04 逾期完成后不再显示逾期，但仍计入逾期率")
        void completedLateCountsInRateButIsNotOverdue() {
            OffsetDateTime due = NOW.minusDays(2);
            OffsetDateTime finish = NOW.minusDays(1);

            assertThat(OverdueCalculator.isOverdue(TaskStatus.DONE, due, NOW)).isFalse();
            assertThat(OverdueCalculator.isCompletedLate(TaskStatus.DONE, due, finish)).isTrue();
            assertThat(OverdueCalculator.countsAsOverdue(TaskStatus.DONE, due, finish, NOW)).isTrue();
        }

        @Test
        @DisplayName("准时完成不计入逾期率")
        void completedOnTimeDoesNotCount() {
            OffsetDateTime due = NOW.plusDays(1);
            OffsetDateTime finish = NOW;

            assertThat(OverdueCalculator.isCompletedLate(TaskStatus.DONE, due, finish)).isFalse();
            assertThat(OverdueCalculator.countsAsOverdue(TaskStatus.DONE, due, finish, NOW)).isFalse();
        }

        @Test
        @DisplayName("已取消任务不计入逾期率")
        void canceledDoesNotCount() {
            OffsetDateTime due = NOW.minusDays(5);
            assertThat(OverdueCalculator.countsAsOverdue(TaskStatus.CANCELED, due, null, NOW)).isFalse();
        }

        @Test
        @DisplayName("逾期天数向上取整")
        void overdueDaysRoundsUp() {
            assertThat(OverdueCalculator.overdueDays(TaskStatus.TODO, NOW.minusHours(1), NOW)).isEqualTo(1);
            assertThat(OverdueCalculator.overdueDays(TaskStatus.TODO, NOW.minusDays(1), NOW)).isEqualTo(1);
            assertThat(OverdueCalculator.overdueDays(TaskStatus.TODO, NOW.minusDays(2), NOW)).isEqualTo(2);
            assertThat(OverdueCalculator.overdueDays(TaskStatus.TODO, NOW.plusDays(1), NOW)).isZero();
        }
    }

    @Nested
    @DisplayName("§4.1 状态流转")
    class TransitionRules {

        @Test
        @DisplayName("AC-05 已完成任务不允许变更为已取消")
        void doneCannotBeCanceled() {
            assertThatThrownBy(() -> StatusTransition.assertCanTransit(TaskStatus.DONE, TaskStatus.CANCELED))
                    .isInstanceOf(BizException.class)
                    .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                            .isEqualTo(ErrorCode.BUSINESS_ERROR));
        }

        @Test
        @DisplayName("已取消任务不允许变更为已完成")
        void canceledCannotBeDone() {
            assertThat(StatusTransition.canTransit(TaskStatus.CANCELED, TaskStatus.DONE)).isFalse();
        }

        @Test
        @DisplayName("合法流转全部放行")
        void legalTransitionsPass() {
            assertThat(StatusTransition.canTransit(TaskStatus.TODO, TaskStatus.DOING)).isTrue();
            assertThat(StatusTransition.canTransit(TaskStatus.TODO, TaskStatus.DONE)).isTrue();
            assertThat(StatusTransition.canTransit(TaskStatus.TODO, TaskStatus.CANCELED)).isTrue();
            assertThat(StatusTransition.canTransit(TaskStatus.DOING, TaskStatus.TODO)).isTrue();
            assertThat(StatusTransition.canTransit(TaskStatus.DOING, TaskStatus.DONE)).isTrue();
            assertThat(StatusTransition.canTransit(TaskStatus.DOING, TaskStatus.CANCELED)).isTrue();
            assertThat(StatusTransition.canTransit(TaskStatus.DONE, TaskStatus.TODO)).isTrue();
            assertThat(StatusTransition.canTransit(TaskStatus.CANCELED, TaskStatus.TODO)).isTrue();
        }

        @Test
        @DisplayName("同状态流转视为幂等空操作")
        void sameStatusIsIdempotent() {
            for (int status : new int[]{TaskStatus.TODO, TaskStatus.DOING, TaskStatus.DONE, TaskStatus.CANCELED}) {
                assertThat(StatusTransition.canTransit(status, status)).isTrue();
            }
        }

        @Test
        @DisplayName("越界状态值被拒绝")
        void invalidStatusRejected() {
            assertThatThrownBy(() -> StatusTransition.assertCanTransit(TaskStatus.TODO, 9))
                    .isInstanceOf(BizException.class);
        }
    }

    @Nested
    @DisplayName("§4.3 象限与优先级")
    class QuadrantRules {

        @Test
        @DisplayName("AC-01 只选象限未选优先级时给出建议值")
        void suggestPriorityFollowsQuadrant() {
            assertThat(QuadrantResolver.suggestPriority(true, true)).isEqualTo(1);
            assertThat(QuadrantResolver.suggestPriority(true, false)).isEqualTo(2);
            assertThat(QuadrantResolver.suggestPriority(false, true)).isEqualTo(3);
            assertThat(QuadrantResolver.suggestPriority(false, false)).isEqualTo(4);
        }

        @Test
        @DisplayName("象限定位正确")
        void quadrantResolved() {
            assertThat(QuadrantResolver.quadrant(true, true)).isEqualTo(QuadrantResolver.Q1);
            assertThat(QuadrantResolver.quadrant(true, false)).isEqualTo(QuadrantResolver.Q2);
            assertThat(QuadrantResolver.quadrant(false, true)).isEqualTo(QuadrantResolver.Q3);
            assertThat(QuadrantResolver.quadrant(false, false)).isEqualTo(QuadrantResolver.Q4);
        }
    }

    @Nested
    @DisplayName("§4.1 状态辅助")
    class StatusHelper {

        @Test
        @DisplayName("未结束状态判定")
        void pendingStatus() {
            assertThat(TaskStatus.isPending(TaskStatus.TODO)).isTrue();
            assertThat(TaskStatus.isPending(TaskStatus.DOING)).isTrue();
            assertThat(TaskStatus.isPending(TaskStatus.DONE)).isFalse();
            assertThat(TaskStatus.isPending(TaskStatus.CANCELED)).isFalse();
            assertThat(TaskStatus.isPending(null)).isFalse();
        }

        @Test
        @DisplayName("合法状态范围校验")
        void validRange() {
            assertThat(TaskStatus.isValid(0)).isTrue();
            assertThat(TaskStatus.isValid(3)).isTrue();
            assertThat(TaskStatus.isValid(4)).isFalse();
            assertThat(TaskStatus.isValid(-1)).isFalse();
            assertThat(TaskStatus.isValid(null)).isFalse();
        }
    }
}
