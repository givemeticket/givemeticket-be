package kr.givemeticket.api.global.auth.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Authorization 헤더의 제공자 토큰에서 뽑아낸 {@code ProviderPrincipal} 을 주입한다.
 * /code 응답으로 받은 토큰을 쓰는 로그인·회원가입에서만 사용한다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Provider {

}
