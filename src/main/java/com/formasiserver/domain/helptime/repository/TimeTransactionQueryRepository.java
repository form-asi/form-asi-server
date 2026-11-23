package com.formasiserver.domain.helptime.repository;

import com.formasiserver.domain.helptime.dto.request.HelpTimeFilter;
import com.formasiserver.domain.helptime.dto.response.HelpTimeTransactionResponse;
import java.util.List;

public interface TimeTransactionQueryRepository {

    List<HelpTimeTransactionResponse> findHistory(long accountId, HelpTimeFilter filter);
}
