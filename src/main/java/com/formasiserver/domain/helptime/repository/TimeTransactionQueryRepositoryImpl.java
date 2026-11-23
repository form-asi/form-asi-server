package com.formasiserver.domain.helptime.repository;

import static com.formasiserver.domain.helptime.entity.QSignupBonus.signupBonus;
import static com.formasiserver.domain.helptime.entity.QTimeAccount.timeAccount;
import static com.formasiserver.domain.helptime.entity.QTimeTransaction.timeTransaction;
import static com.formasiserver.domain.helptime.entity.QTimeTransfer.timeTransfer;

import com.formasiserver.domain.helptime.dto.request.HelpTimeFilter;
import com.formasiserver.domain.helptime.dto.response.HelpTimeTransactionResponse;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TimeTransactionQueryRepositoryImpl implements TimeTransactionQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<HelpTimeTransactionResponse> findHistory(long accountId, HelpTimeFilter filter) {
        NumberExpression<Long> changeMinutes = timeTransaction.amount.longValue().add(timeTransaction.bonusAmount);
        BooleanExpression matchesFilter = switch (filter) {
            case ALL -> changeMinutes.ne(0L);
            case EARNED -> changeMinutes.gt(0L);
            case SPENT -> changeMinutes.lt(0L);
        };

        return queryFactory.select(Projections.constructor(HelpTimeTransactionResponse.class,
                        timeTransaction.createdAt, timeTransfer.type, changeMinutes,
                        timeTransaction.balanceAfter, signupBonus.expiresAt))
                .from(timeTransaction)
                .join(timeTransaction.account, timeAccount)
                .join(timeTransaction.transfer, timeTransfer)
                .leftJoin(signupBonus)
                .on(signupBonus.user.eq(timeAccount.user), timeTransfer.type.eq("SIGNUP_BONUS_GRANTED"))
                .where(timeAccount.id.eq(accountId), matchesFilter)
                .orderBy(timeTransaction.createdAt.desc(), timeTransaction.id.desc())
                .fetch();
    }
}
