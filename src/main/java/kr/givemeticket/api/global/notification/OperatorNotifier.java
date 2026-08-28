package kr.givemeticket.api.global.notification;

/**
 * 사람이 봐야 하는 일을 알린다. 대시보드와 달리 <b>알림이 사람을 찾아온다</b>.
 *
 * <p>언제: DLQ 격리처럼 사람의 판단이 필요한 순간.
 */
public interface OperatorNotifier {

    /**
     * 실패를 알린다. <b>절대 호출자를 실패시키지 않는다</b> — 알리려던 사고보다
     * 큰 사고가 나면 안 된다.
     */
    void notifyFailure(String title, String detail);
}
