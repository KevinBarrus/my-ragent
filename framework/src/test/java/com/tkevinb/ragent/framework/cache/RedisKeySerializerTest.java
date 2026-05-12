package com.tkevinb.ragent.framework.cache;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class RedisKeySerializerTest {

    @Test
    void serialize_shouldAddPrefix() {
        RedisKeySerializer serializer = new RedisKeySerializer();
        ReflectionTestUtils.setField(serializer, "keyPrefix", "myapp:");

        byte[] result = serializer.serialize("user:1");

        assertEquals("myapp:user:1", new String(result, StandardCharsets.UTF_8));
    }

    @Test
    void deserialize_shouldReturnString() {
        RedisKeySerializer serializer = new RedisKeySerializer();

        String result = serializer.deserialize("myapp:user:1".getBytes(StandardCharsets.UTF_8));

        assertEquals("myapp:user:1", result);
    }
}
