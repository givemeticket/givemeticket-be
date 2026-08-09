package kr.givemeticket.api.campaign.domain;

/**
 * 캠페인 상세 화면이 어떤 모습으로 그려져야 하는지를 결정한다.
 * 프론트가 응답 필드의 null 여부로 추측하지 않도록 역할을 명시적으로 내려준다.
 */
public enum ViewerRole {

    /** 비로그인. 신청 버튼을 누르면 로그인 화면으로 보낸다. */
    GUEST,

    /** 로그인했지만 아직 신청하지 않음. */
    VIEWER,

    /** 신청해서 유효한 티켓을 가지고 있음. 취소 가능. */
    PARTICIPANT,

    /** 개설자. 오픈 지연·정원 증원·삭제 가능. */
    OWNER
}
