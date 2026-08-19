package kr.givemeticket.api.campaign.application;

import java.lang.reflect.Field;

/**
 * 식별자나 상태처럼 영속화 과정에서 정해지는 값을 테스트에서 직접 심는다.
 */
final class TestEntities {

    private TestEntities() {
    }

    static <T> T with(T entity, String fieldName, Object value) {
        Class<?> type = entity.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(entity, value);
                return entity;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("no such field: " + fieldName);
    }
}
