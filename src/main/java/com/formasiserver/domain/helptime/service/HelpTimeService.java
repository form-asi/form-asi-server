package com.formasiserver.domain.helptime.service;

import com.formasiserver.domain.helptime.dto.request.HelpTimeFilter;
import com.formasiserver.domain.helptime.dto.response.HelpTimeHistoryResponse;
import com.formasiserver.domain.helptime.repository.TimeAccountRepository;
import com.formasiserver.domain.helptime.repository.TimeTransactionRepository;
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

    @Transactional(readOnly = true)
    public HelpTimeHistoryResponse getHistory(UUID userId, HelpTimeFilter filter) {
        return timeAccountRepository.findByUser_UserId(userId)
                .map(account -> new HelpTimeHistoryResponse(account.getBalanceMinutes(),
                        timeTransactionRepository.findHistory(account.getId(), filter)))
                .orElseGet(() -> new HelpTimeHistoryResponse(0, List.of()));
    }
}
