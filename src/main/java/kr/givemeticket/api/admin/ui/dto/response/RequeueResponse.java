package kr.givemeticket.api.admin.ui.dto.response;

/**
 * @param requeued 실제로 메인 큐로 되돌렸는가. 이미 없거나 되돌릴 수 없는 형태면 false
 */
public record RequeueResponse(String id, boolean requeued) {
}
