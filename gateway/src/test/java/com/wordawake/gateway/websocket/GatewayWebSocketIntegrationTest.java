package com.wordawake.gateway.websocket;

import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * WebSocket + Kafka 연동 통합 테스트.
 * - EmbeddedKafka로 실제 토픽 사용
 * - 연결 → sessionId 수신 → 바이너리 전송 → hotword-events 발행 → 해당 클라이언트가 메시지 수신하는지 검증
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
class GatewayWebSocketIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    KafkaTemplate<String, String> stringKafkaTemplate;

    @Value("${app.kafka.topic.hotword-events:hotword-events}")
    String hotwordEventsTopic;

    @Value("${app.kafka.topic.audio-stream:audio-stream}")
    String audioStreamTopic;

    @Value("${spring.kafka.bootstrap-servers}")
    String bootstrapServers;

    WebSocketClient webSocketClient;

    @BeforeEach
    void setUp() throws Exception {
        webSocketClient = new WebSocketClient();
        webSocketClient.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (webSocketClient != null) {
            webSocketClient.stop();
        }
    }

    /**
     * WebSocket 연결 후 일정 시간 동안 수신하는 모든 메시지를 수집해 검증.
     * 서버가 지속적으로 메시지를 보낼 때 잘 수신하는지 확인할 때 사용.
     */
    @Test
    void connect_receivesMessagesContinuously() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        CountDownLatch closeLatch = new CountDownLatch(1);
        Listener listener = new Listener(received, closeLatch);

        URI uri = URI.create("ws://localhost:" + port + "/gateway/socket");
        Session session = webSocketClient.connect(listener, uri).get(5, TimeUnit.SECONDS);

        // 5초 동안 들어오는 메시지 수집 (서버가 지속 전송하는 메시지 수신 검증용)
        List<String> collected = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 100_000;
        while (System.currentTimeMillis() < deadline) {
            String msg = received.poll(1, TimeUnit.SECONDS);
            if (msg != null) {
                collected.add(msg);
            }
        }

        session.close(StatusCode.NORMAL, "done", Callback.NOOP);
        closeLatch.await(2, TimeUnit.SECONDS);

        // 최소 1개(welcome) 이상 수신되었는지 검증
        assertFalse(collected.isEmpty(),
                "연결 후 최소 1개 메시지(welcome)를 수신해야 함. 수신 개수: " + collected.size());
        // 첫 메시지는 연결 안내(welcome)여야 함
        assertTrue(collected.get(0).contains("sessionId") || collected.get(0).contains("connected"),
                "첫 메시지는 welcome(connected/sessionId) 형태여야 함. 수신: " + collected.get(0));
    }

    @Test
    void connect_receivesWelcomeWithSessionId() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        CountDownLatch closeLatch = new CountDownLatch(1);
        Listener listener = new Listener(received, closeLatch);

        URI uri = URI.create("ws://localhost:" + port + "/gateway/socket");
        Session session = webSocketClient.connect(listener, uri).get(5, TimeUnit.SECONDS);

        // 첫 메시지: {"type":"connected","sessionId":"..."}
        String first = received.poll(3, TimeUnit.SECONDS);
        assertNotNull(first);
        assertTrue(first.contains("\"type\":\"connected\""));
        assertTrue(first.contains("\"sessionId\""));

        session.close(StatusCode.NORMAL, "done", Callback.NOOP);
        closeLatch.await(2, TimeUnit.SECONDS);
    }

    @Test
    void textMessage_receivesEcho() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        CountDownLatch closeLatch = new CountDownLatch(1);
        Listener listener = new Listener(received, closeLatch);

        URI uri = URI.create("ws://localhost:" + port + "/gateway/socket");
        Session session = webSocketClient.connect(listener, uri).get(5, TimeUnit.SECONDS);
        // welcome 메시지 소비
        received.poll(2, TimeUnit.SECONDS);

        session.sendText("ping", Callback.NOOP);
        String echo = received.poll(2, TimeUnit.SECONDS);
        assertEquals("gateway echo: ping", echo);

        session.close(StatusCode.NORMAL, "done", Callback.NOOP);
        closeLatch.await(2, TimeUnit.SECONDS);
    }

    @Test
    void binaryMessage_isPublishedToAudioStreamTopic() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        CountDownLatch closeLatch = new CountDownLatch(1);
        Listener listener = new Listener(received, closeLatch);

        URI uri = URI.create("ws://localhost:" + port + "/gateway/socket");
        Session session = webSocketClient.connect(listener, uri).get(5, TimeUnit.SECONDS);
        String welcome = received.poll(3, TimeUnit.SECONDS);
        String sessionId = extractSessionId(welcome);
        assertNotNull(sessionId);

        byte[] chunk = new byte[]{1, 2, 3, 4, 5};
        session.sendBinary(ByteBuffer.wrap(chunk), Callback.NOOP);
        // ACK 수신
        String ack = received.poll(2, TimeUnit.SECONDS);
        assertNotNull(ack);
        assertTrue(ack.contains("ack"));

        // audio-stream 토픽에서 해당 sessionId의 레코드 1건 수신 확인
        try (KafkaConsumer<String, byte[]> consumer = audioConsumer()) {
            consumer.subscribe(Collections.singletonList(audioStreamTopic));
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(5));
            assertFalse(records.isEmpty(), "audio-stream에 레코드가 발행되어야 함");
            records.forEach(r -> {
                assertEquals(sessionId, r.key());
                assertArrayEquals(chunk, r.value());
            });
        }

        session.close(StatusCode.NORMAL, "done", Callback.NOOP);
        closeLatch.await(2, TimeUnit.SECONDS);
    }

    private KafkaConsumer<String, byte[]> audioConsumer() {
        java.util.Map<String, Object> props = new java.util.HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-audio-consumer-" + System.currentTimeMillis());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props);
        return consumer;
    }

    @Test
    void whenHotwordEventPublished_clientReceivesMessage() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        CountDownLatch closeLatch = new CountDownLatch(1);
        Listener listener = new Listener(received, closeLatch);

        URI uri = URI.create("ws://localhost:" + port + "/gateway/socket");
        Session session = webSocketClient.connect(listener, uri).get(5, TimeUnit.SECONDS);

        // 첫 메시지에서 sessionId 추출 (간단 파싱)
        String welcome = received.poll(3, TimeUnit.SECONDS);
        assertNotNull(welcome);
        String sessionId = extractSessionId(welcome);
        assertNotNull(sessionId);

        // Hotword 서비스 역할: hotword-events에 발행
        String payload = "{\"word\":\"hello\"}";
        CompletableFuture<SendResult<String, String>> future =
                stringKafkaTemplate.send(hotwordEventsTopic, sessionId, payload);
        future.get(5, TimeUnit.SECONDS);

        // 해당 클라이언트가 동일 내용 수신해야 함
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            String msg = received.poll(1, TimeUnit.SECONDS);
            assertNotNull(msg, "hotword 메시지를 받지 못함");
            assertTrue(msg.contains("hello") && msg.contains("word"), "수신 메시지: " + msg);
        });

        session.close(StatusCode.NORMAL, "done", Callback.NOOP);
        closeLatch.await(2, TimeUnit.SECONDS);
    }

    private static String extractSessionId(String welcomeJson) {
        int start = welcomeJson.indexOf("\"sessionId\":\"") + 13;
        if (start < 13) return null;
        int end = welcomeJson.indexOf("\"", start);
        return end > start ? welcomeJson.substring(start, end) : null;
    }

    /** 테스트에서 hotword-events에 String 발행용 KafkaTemplate */
    @TestConfiguration
    static class TestKafkaConfig {
        @Bean
        public org.springframework.kafka.core.ProducerFactory<String, String> stringProducerFactory(
                @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
            java.util.Map<String, Object> props = new java.util.HashMap<>();
            props.put(org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                    org.apache.kafka.common.serialization.StringSerializer.class);
            props.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                    org.apache.kafka.common.serialization.StringSerializer.class);
            return new org.springframework.kafka.core.DefaultKafkaProducerFactory<>(props);
        }

        @Bean
        public KafkaTemplate<String, String> stringKafkaTemplate(
                org.springframework.kafka.core.ProducerFactory<String, String> stringProducerFactory) {
            return new KafkaTemplate<>(stringProducerFactory);
        }
    }

    public static class Listener implements Session.Listener {
        private final BlockingQueue<String> received;
        private final CountDownLatch closeLatch;
        private Session session;

        Listener(BlockingQueue<String> received, CountDownLatch closeLatch) {
            this.received = received;
            this.closeLatch = closeLatch;
        }

        @Override
        public void onWebSocketOpen(Session s) {
            this.session = s;
            System.out.println("✅ Client: WS 연결됨 - " + s);
            session.demand();
        }

        @Override
        public void onWebSocketText(String message) {
            System.out.println("📨 Client: Gateway 응답 받음 - " + message);
            received.offer(message);
            session.demand();
        }

        @Override
        @SuppressWarnings("removal")
        public void onWebSocketClose(int statusCode, String reason) {
            System.out.println("🔌 Client: 연결 종료 - " + statusCode + " (" + reason + ")");
            closeLatch.countDown();
        }
    }
}
