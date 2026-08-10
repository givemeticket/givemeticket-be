package kr.givemeticket.api.global.auth.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Authorization 헤더의 액세스 토큰에서 뽑아낸 userId 를 주입한다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LoginUserId {

    /**
     * false면 헤더가 아예 없을 때 401 대신 null 이 주입된다.
     * 링크로 들어온 비로그인 사용자에게도 행사 정보를 보여줘야 하는 조회 API에서 쓴다.
     * 헤더를 보냈는데 토큰이 잘못된 경우는 required 와 무관하게 401이다.
     */
    boolean required() default true;
}
