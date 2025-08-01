package com.example.bitwayws.redis.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeAggWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;

    private final Map<String, CustomSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = session.getId();
        UriComponents uriComponents = UriComponentsBuilder.fromUri(session.getUri()).build();
        String symbol = uriComponents.getQueryParams().getFirst("symbol");
        sessions.put(sessionId, new CustomSession(session, symbol));
        log.info("WebSocket 연결됨: {}, symbol: {}", sessionId, symbol);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String sessionId = session.getId();
        log.info("수신 메시지 [{}]: {}", sessionId, message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        log.info("WebSocket 종료됨: {}, code={}, reason={}", sessionId, status.getCode(), status.getReason());
    }

    public void broadcast(Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("WebSocket 직렬화 실패", e);
            return;
        }

        ExecutorService executor = Executors.newCachedThreadPool();
        sessions.values().forEach(session -> {
            executor.submit(() -> {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(json));
                    } else {
                        sessions.remove(session.getId());
                    }
                } catch (Exception e) {
                    log.error("메시지 전송 실패", e);
                    sessions.remove(session.getId());
                }
            });
        });
    }

    public void broadcastToSymbol(String symbol, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("WebSocket 직렬화 실패", e);
            return;
        }

        sessions.values().forEach(session -> {
            try {
                if (session.isOpen() && symbol.equals(session.getSymbol())) {
                    session.sendMessage(new TextMessage(json));
                }
            } catch (Exception e) {
                log.error("전송 실패: " + session.getId(), e);
            }
        });
    }

    public int getActiveSessionCount() {
        return (int) sessions.values().stream()
                .filter(CustomSession::isOpen)
                .count();
    }

    @Scheduled(fixedDelay = 30000)
    public void cleanClosedSessions() {
        sessions.entrySet().removeIf(entry -> !entry.getValue().isOpen());
    }

    private static class CustomSession {
        private final WebSocketSession session;
        private final String symbol;

        public CustomSession(WebSocketSession session, String symbol) {
            this.session = session;
            this.symbol = symbol;
        }

        public boolean isOpen() {
            return session.isOpen();
        }

        public void sendMessage(TextMessage message) throws Exception {
            session.sendMessage(message);
        }

        public String getId() {
            return session.getId();
        }

        public String getSymbol() {
            return symbol;
        }
    }
}