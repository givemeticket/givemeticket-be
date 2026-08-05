package kr.givemeticket.api.global.log.dto;

import java.util.Map;

/**
 * 구조화 로그 한 건.
 *
 * <p>{@code fields()} 는 JSON 로그의 최상위 필드로 펼쳐지고(Loki 에서 검색/필터 대상),
 * {@code summary()} 는 사람이 눈으로 읽는 한 줄이다.
 */
public interface Log {

    Map<String, Object> fields();

    String summary();
}
