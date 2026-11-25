package com.formasiserver.domain.helptime.entity;

import com.formasiserver.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "time_transaction")
public class TimeTransaction extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "time_account_id", nullable = false)
    private TimeAccount account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "time_transfer_id", nullable = false)
    private TimeTransfer transfer;

    @Column(nullable = false)
    private int amount;

    @Column(name = "bonus_amount", nullable = false)
    private int bonusAmount;

    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    public static TimeTransaction of(TimeAccount account, TimeTransfer transfer, int amount, int bonusAmount) {
        TimeTransaction transaction = new TimeTransaction();
        transaction.account = account;
        transaction.transfer = transfer;
        transaction.amount = amount;
        transaction.bonusAmount = bonusAmount;
        transaction.balanceAfter = account.getBalanceMinutes();
        return transaction;
    }
}
