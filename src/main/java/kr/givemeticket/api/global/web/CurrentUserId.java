package kr.givemeticket.api.global.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserId {

    /**
     * false면 비로그인 요청에서 401 대신 null이 주입된다.
     * 링크로 들어온 비로그인 사용자에게도 행사 정보를 보여줘야 하는 조회 API에서 쓴다.
     */
    boolean required() default true;
}
