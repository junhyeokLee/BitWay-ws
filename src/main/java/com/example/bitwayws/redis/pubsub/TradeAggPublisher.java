package com.example.bitwayws.redis.pubsub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TradeAggPublisher {

    private final StringRedisTemplate redisTemplate;
    private static final String CHANNEL_PREFIX = "trade:";

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 100;
    private static final int MAX_MESSAGE_LENGTH = 3000;

    // Redis Pub/Sub 전송 (WebSocket 직접 전송은 Subscriber가 처리)
    public void publish(String symbol, String message) {
        String channel = CHANNEL_PREFIX + symbol.toLowerCase();

        if (message.length() > MAX_MESSAGE_LENGTH) {
            log.warn("[Redis-Pub] {} 채널에 보낼 메시지가 너무 깁니다 ({}자)", symbol, message.length());
            return;
        }

        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                Long receivers = redisTemplate.convertAndSend(channel, message);

                if (receivers == null || receivers == 0) {
                    log.warn("[Redis-Pub] 구독자가 없는 채널입니다: {}", channel);
                }
                return; // success
            } catch (Exception e) {
                log.error("[Redis-Pub] {} 채널에 트레이드 전송 실패 (시도 {}/{}): {}", channel, i + 1, MAX_RETRIES, e.getMessage());
                try {
                    Thread.sleep(RETRY_DELAY_MS * (i + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        log.error("[Redis-Pub] {} 채널에 최종 전송 실패 ({}회 시도됨)", channel, MAX_RETRIES);
    }
}