package com.formasiserver.domain.helptime.service;

import com.formasiserver.domain.helptime.dto.request.HelpTimeFilter;
import com.formasiserver.domain.helptime.dto.response.HelpTimeHistoryResponse;
import com.formasiserver.domain.helptime.entity.TimeAccount;
import com.formasiserver.domain.helptime.entity.TimeTransaction;
import com.formasiserver.domain.helptime.entity.TimeTransfer;
import com.formasiserver.domain.helptime.repository.SignupBonusRepository;
import com.formasiserver.domain.helptime.repository.TimeAccountRepository;
import com.formasiserver.domain.helptime.repository.TimeTransactionRepository;
import com.formasiserver.domain.helptime.repository.TimeTransferRepository;
import com.formasiserver.domain.survey.entity.Survey;
import com.formasiserver.global.exception.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HelpTimeService {

    private final TimeAccountRepository timeAccountRepository;
    private final TimeTransactionRepository timeTransactionRepository;
    private final TimeTransferRepository timeTransferRepository;
    private final SignupBonusRepository signupBonusRepository;

    @Transactional(readOnly = true)
    public HelpTimeHistoryResponse getHistory(UUID userId, HelpTimeFilter filter) {
        return timeAccountRepository.findByUser_UserId(userId)
                .map(account -> new HelpTimeHistoryResponse(account.getBalanceMinutes(),
                        timeTransactionRepository.findHistory(account.getId(), filter)))
                .orElseGet(() -> new HelpTimeHistoryResponse(0, List.of()));
    }

    @Transactional
    public long reserveForSurveyRegistration(Survey survey) {
        int requiredMinutes = Math.multiplyExact(survey.getDurationMinutes(), survey.getTargetCount());
        UUID userId = survey.getUser().getUserId();
        TimeAccount userAccount = timeAccountRepository.findForUpdateByUser_UserId(userId)
                .orElseThrow(() -> ApiException.insufficientHelpTime(requiredMinutes, 0));
        Instant now = Instant.now();
        boolean bonusActive = signupBonusRepository.findByUser_UserId(userId)
                .map(bonus -> bonus.isUsableAt(now))
                .orElse(false);

        TimeAccount reserve = timeAccountRepository.save(userAccount.reserveForSurvey(survey, requiredMinutes, bonusActive));
        TimeTransfer transfer = timeTransferRepository.save(TimeTransfer.forSurveyRegistration(survey.getId()));

        int regular = reserve.getBalance();
        int bonus = reserve.getBonusBalance();
        timeTransactionRepository.saveAll(List.of(
                TimeTransaction.of(userAccount, transfer, -regular, -bonus),
                TimeTransaction.of(reserve, transfer, regular, bonus)
        ));
        return userAccount.getBalanceMinutes();
    }
}
