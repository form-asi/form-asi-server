package com.formasiserver.domain.helptime.dto.response;

import java.util.List;

public record HelpTimeHistoryResponse(long helpTimeMinutes, List<HelpTimeTransactionResponse> items) {
}
