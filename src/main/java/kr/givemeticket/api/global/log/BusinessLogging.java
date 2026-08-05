package kr.givemeticket.api.global.log;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 붙은 메서드가 끝날 때 BUSINESS 타입 로그를 남긴다.
 *
 * @see BusinessLogAspect
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BusinessLogging {

    /**
     * 어떤 액션인지 (예: "캠페인 신청")
     */
    String value();
}
