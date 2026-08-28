package kr.givemeticket.api.global.domain;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

/**
 * DB 가 식별자를 발급하는 엔티티의 상위 클래스.
 *
 * <p>id 를 애플리케이션이 직접 정해야 하는 엔티티는 이것 대신
 * {@link BaseTimeEntity} 를 상속하고 자기 {@code @Id} 를 선언한다.
 */
@Getter
@MappedSuperclass
public abstract class BaseEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
}
