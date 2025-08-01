package com.example.bitwayws.interceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
public class TradeHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        log.info("[WebSocket] 핸드셰이크 시작: URI={} Headers={}", request.getURI(), request.getHeaders());
        // 여기에서 인증 또는 심볼 파싱 후 attribute에 저장 가능
        return true; // false 반환 시 연결 거부
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        log.info("[WebSocket] 핸드셰이크 완료: URI={}", request.getURI());
    }
}
