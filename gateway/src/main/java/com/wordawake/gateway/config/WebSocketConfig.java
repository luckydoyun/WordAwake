package com.wordawake.gateway.config;

import com.wordawake.gateway.websocket.GatewayHandler;
import com.wordawake.gateway.websocket.GatewayInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Slf4j
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GatewayHandler gatewayHandler;

    // 10M (1024 * 1024 * 10)
    @Value("${websocket.max-text-message-buffer-size:10485760}")
    private int maxTextMessageBufferSize;

    // 1M (1024 * 1024 * 1)
    @Value("${websocket.max-binary-message-buffer-size:1048576}")
    private int maxBinaryMessageBufferSize;

    // 10M (1024 * 1024 * 100)
    @Value("${websocket.max-session-idle-timeout:9223372036854775807}")
    private long maxSessionIdleTimeout;

    @Autowired
    public WebSocketConfig(GatewayHandler gatewayHandler) {
        this.gatewayHandler = gatewayHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        log.info("[Gateway Server] Register websocket config");
        registry.addHandler(gatewayHandler, "/socket")
                .addInterceptors(new GatewayInterceptor())
                .setAllowedOriginPatterns("*")
        ;
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        // 메세지 버퍼 크기, 시간 초과 등과 같은 런타임 특성을 제어 할 수 있다.
        // Tomcat, WildFly, GlassFish 에 경우에 ServletServerContainerFactoryBean 사용 가능
        log.info("[Gateway Server] websocket configuration property");
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(maxTextMessageBufferSize);
        container.setMaxBinaryMessageBufferSize(maxBinaryMessageBufferSize);
        container.setMaxSessionIdleTimeout(maxSessionIdleTimeout); // 무한대
        return container;
    }
}
