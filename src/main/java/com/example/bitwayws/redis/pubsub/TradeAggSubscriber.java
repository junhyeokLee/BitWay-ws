package com.example.bitwayws.redis.pubsub;

import com.example.bitwayws.dto.BinanceAggTradeResDto;
import com.example.bitwayws.redis.handler.TradeAggWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
@Slf4j
public class TradeAggSubscriber implements MessageListener {

    private final TradeAggWebSocketHandler tradeWebSocketHandler;
    private final ObjectMapper objectMapper;

    private static final AtomicLong redisMessageCount = new AtomicLong(0);

    @Override
    public void onMessage(Message message, byte[] pattern) {
        redisMessageCount.incrementAndGet();
        String topic = new String(message.getChannel());
        String body = new String(message.getBody());
        try {
            if (topic.startsWith("trade:")) {
                String symbol = topic.split(":")[1];
                if (body != null && body.trim().startsWith("{")) {
                    BinanceAggTradeResDto trade = objectMapper.readValue(body, BinanceAggTradeResDto.class);
                    tradeWebSocketHandler.broadcastToSymbol(symbol, trade);
                } else {
                    log.warn("trade 채널 메시지가 JSON 형식이 아님: {}", body);
                }
            } else if (topic.startsWith("analysis:")) {
                tradeWebSocketHandler.broadcast(body);
            } else {
                log.warn("알 수 없는 Redis 채널: {}", topic);
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Redis 메시지 파싱 실패: {}", body, e);
        } catch (Exception e) {
            log.error("Exception in onMessage: ", e);
        }
    }

    public long getRedisMessageCount() {
        return redisMessageCount.get();
    }
}