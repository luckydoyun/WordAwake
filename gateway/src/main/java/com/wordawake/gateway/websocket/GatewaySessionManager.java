package com.wordawake.gateway.websocket;

import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class GatewaySessionManager {

    // ConcurrentHashMap<sessionId, WebSocketSession> (스레드 안전)
    private final ConcurrentHashMap<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    /**
     * 연결 시 세션 등록
     */
    public void addSession(WebSocketSession session) {
        String sessionId = session.getId();
        activeSessions.put(sessionId, session);
        log.info("🟢 세션 추가 - ID:{}, 총 연결:{}명", sessionId, activeSessions.size());
    }

    /**
     * 연결 종료 시 세션 제거
     */
    public void removeSession(WebSocketSession session) {
        String sessionId = session.getId();
        activeSessions.remove(sessionId);
        log.info("🔴 세션 제거 - ID:{}, 총 연결:{}명", sessionId, activeSessions.size());
    }

    /**
     * n 초마다 모든 활성 세션에 하트비트 전송
     */
//  예시)  @Scheduled(fixedRate = 10000)  // 10초마다 실행
    @Scheduled(fixedRate = 30000)  // 30초마다 실행
    public void sendHeartbeatToAll() {
        Gson gson = new Gson();  // 임시 직접 생성

        Map<String, Object> heartbeat = new HashMap<>();
        heartbeat.put("status", 200);
        heartbeat.put("status-msg", "정상");
        heartbeat.put("hotword", "켜줘(테스트)");
        heartbeat.put("type", "command");
        heartbeat.put("intent", "control");
        heartbeat.put("action", "turn-on");
        heartbeat.put("time", Instant.now().toString());

        String heartbeatMsg = gson.toJson(heartbeat);  // 5줄!

        int successCount = 0;
        int deadCount = 0;

        // 모든 세션 순회
        for (String sessionId : activeSessions.keySet()) {
            WebSocketSession session = activeSessions.get(sessionId);

            if (session == null || !session.isOpen()) {
                activeSessions.remove(sessionId);  // 죽은 세션 정리
                deadCount++;
                continue;
            }

            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(heartbeatMsg));
                    successCount++;
                }
            } catch (Exception e) {
                log.warn("하트비트 전송 실패 - ID:{}, 이유:{}", sessionId, e.getMessage());
                activeSessions.remove(sessionId);  // 전송 실패 시 제거
                deadCount++;
            }
        }

        log.info("✅ Hot Word 감지로 인한 메세지 전송 완료 - 성공:{}, 실패:{}, 총 세션:{}",
                successCount, deadCount, activeSessions.size());
    }

    /**
     * 특정 세션 ID에만 메시지 전송 (옵션)
     */
    public void sendToSession(String sessionId, String message) {
        WebSocketSession session = activeSessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
                log.debug("개별 전송 성공 - ID:{}", sessionId);
            } catch (Exception e) {
                log.warn("개별 전송 실패 - ID:{}, 이유:{}", sessionId, e.getMessage());
                activeSessions.remove(sessionId);
            }
        }
    }

    /**
     * 현재 연결 세션 수
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }
}
