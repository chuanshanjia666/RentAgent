package com.rentagent.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiEmbeddingClientTest {

    @Test
    @DisplayName("UT-EMB-01 缺少 baseUrl 或 apiKey 时 available 为 false")
    void checksAvailability() {
        OpenAiEmbeddingClient client1 = new OpenAiEmbeddingClient("", "", "bge-m3", Duration.ofSeconds(5));
        assertFalse(client1.available());

        OpenAiEmbeddingClient client2 = new OpenAiEmbeddingClient("http://localhost", "", "bge-m3", Duration.ofSeconds(5));
        assertFalse(client2.available());

        OpenAiEmbeddingClient client3 = new OpenAiEmbeddingClient("http://localhost", "sk-test", "bge-m3", Duration.ofSeconds(5));
        assertTrue(client3.available());
    }

    @Test
    @DisplayName("UT-EMB-02 空输入文本返回空数组/空列表")
    void handlesEmptyInput() {
        OpenAiEmbeddingClient client = new OpenAiEmbeddingClient("http://localhost", "sk-test", "bge-m3", Duration.ofSeconds(5));
        assertEquals(0, client.embed(null).length);
        assertEquals(0, client.embed("   ").length);
        assertTrue(client.embedBatch(null).isEmpty());
        assertTrue(client.embedBatch(List.of()).isEmpty());
    }
}

