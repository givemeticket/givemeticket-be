package kr.givemeticket.api.admin.ui.dto.response;

import java.util.List;

public record DeadLetterListResponse(long total, List<DeadLetterResponse> items) {
}
