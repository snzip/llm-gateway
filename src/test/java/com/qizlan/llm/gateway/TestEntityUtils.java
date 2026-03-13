package com.qizlan.llm.gateway;

import com.qizlan.llm.gateway.persistence.entity.BaseEntity;
import java.lang.reflect.Field;

public final class TestEntityUtils {

    private TestEntityUtils() {
    }

    public static void setId(BaseEntity entity, String id) {
        try {
            Field idField = BaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
