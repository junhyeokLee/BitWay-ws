package com.example.bitwayws.redis.service;

import com.example.bitwayws.dto.BinanceAggTradeResDto;
import com.example.bitwayws.dto.TradeAnalysisLogResDto;
import com.example.bitwayws.dto.WhaleTradeResDto;
import com.example.bitwayws.redis.pubsub.TradeAggPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradeAggAnalysisService {

    private final TradeAggPublisher tradePublisher;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private final Map<String, Long> lastAnalyzedMap = new ConcurrentHashMap<>();

    // Add a trade to the buffer, wrapping with timestamp
//    public void addTrade(String symbol, BinanceAggTradeResDto trade) {
//        try {
//            log.info("[WebSocket 실시간 전송] {} 트레이드 전송 완료", symbol);
//            String publishJson = objectMapper.writeValueAsString(trade);
//            tradePublisher.publish(symbol, publishJson);
//
//            // Redis에 저장 (누적 저장)
//            String key = "trades:" + symbol.toLowerCase();
//            String json = objectMapper.writeValueAsString(trade);
//            redisTemplate.opsForList().rightPush(key, json);
//            redisTemplate.opsForList().trim(key, -1000, -1);
//            redisTemplate.expire(key, Duration.ofDays(1));
//            log.info("[Redis 저장] {} 트레이드 Redis 저장 완료", symbol);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

    public void processTrade(BinanceAggTradeResDto trade) {
        if (trade == null) return;
        publishOnly(trade.getSymbol(), trade);
        analyzeIfNeeded(trade.getSymbol());
    }

    private long getTodayStartMillis() {
        return java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Seoul"))
            .withHour(8).withMinute(0).withSecond(0).withNano(0)
            .toInstant().toEpochMilli();
    }



    // Analyze only for trades of a given symbol
    private void analyzeIfNeeded(String symbol) {
        long now = System.currentTimeMillis();
        if (now - lastAnalyzedMap.getOrDefault(symbol, 0L) < 10_000) return; // 10초 간격 제한
        lastAnalyzedMap.put(symbol, now);

        long todayStartTimestamp = getTodayStartMillis();
        List<BinanceAggTradeResDto> recentTrades = getRecentTrades(symbol).stream()
            .filter(t -> t.getTimestamp() >= todayStartTimestamp)
            .toList();
        if (!recentTrades.isEmpty()) {
            String sym = recentTrades.get(0).getSymbol();
            long endTime = recentTrades.stream().mapToLong(BinanceAggTradeResDto::getTimestamp).max().orElse(now);
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(java.time.ZoneId.of("Asia/Seoul"));

            double buyVolume = recentTrades.stream()
                    .filter(t -> !t.isBuyerMaker())
                    .mapToDouble(t -> t.getPrice() * t.getQuantity()).sum();

            double sellVolume = recentTrades.stream()
                    .filter(BinanceAggTradeResDto::isBuyerMaker)
                    .mapToDouble(t -> t.getPrice() * t.getQuantity()).sum();

            Map<Integer, Long> levelCounts = recentTrades.stream()
                    .collect(Collectors.groupingBy(this::classifyTradeLevel, Collectors.counting()));

            List<WhaleTradeResDto> whaleTrades = recentTrades.stream()
                    .filter(t -> classifyTradeLevel(t) == 11)
                    .sorted(java.util.Comparator.comparingLong(BinanceAggTradeResDto::getTimestamp))
                    .map(t -> WhaleTradeResDto.builder()
                            .side(t.isBuyerMaker() ? "매도" : "매수")
                            .quantity(t.getQuantity())
                            .price(t.getPrice())
                            .total(t.getQuantity() * t.getPrice())
                            .timestamp(formatter.format(java.time.Instant.ofEpochMilli(t.getTimestamp())))
                            .build())
                    .collect(Collectors.toList());

            TradeAnalysisLogResDto logDto = TradeAnalysisLogResDto.builder()
                    .symbol(sym)
                    .tradeLevels(levelCounts)
                    .buyVolume(buyVolume)
                    .sellVolume(sellVolume)
                    .diffVolume(Math.abs(buyVolume - sellVolume))
                    .volatilityDetected(Math.abs(buyVolume - sellVolume) > 1000)
                    .whaleTrades(whaleTrades)
                    .latestTradeTime(formatter.format(java.time.Instant.ofEpochMilli(endTime)))
                    .build();

            try {
                String json = objectMapper.writeValueAsString(logDto);

                tradePublisher.publish(symbol, json);

                String analysisKey = "analysis:" + symbol.toLowerCase();
                redisTemplate.opsForList().rightPush(analysisKey, json);
                redisTemplate.expire(analysisKey, Duration.ofDays(1));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }
    }

    private int classifyTradeLevel(BinanceAggTradeResDto trade) {
        double amount = trade.getPrice() * trade.getQuantity();
        if (amount >= 100_000) return 11; // Whale
        return (int)(amount / 10_000) + 1;
    }

    public Map<Integer, List<BinanceAggTradeResDto>> getTradeLevels(String symbol) {
        return getRecentTrades(symbol).stream()
                .collect(Collectors.groupingBy(this::classifyTradeLevel));
    }

    // Return trades of given symbol
    public List<BinanceAggTradeResDto> getRecentTrades(String symbol) {
        String key = "trades:" + symbol.toLowerCase();
        List<String> rawJsonList = redisTemplate.opsForList().range(key, -100, -1);
        if (rawJsonList == null) return List.of();

        return rawJsonList.stream()
                .map(json -> {
                    try {
                        return objectMapper.readValue(json, BinanceAggTradeResDto.class);
                    } catch (JsonProcessingException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }


    public Map<Integer, Long> getTodaySymbolTradeLevelCounts(String symbol) {
        return getRecentTrades(symbol).stream()
                .collect(Collectors.groupingBy(this::classifyTradeLevel, Collectors.counting()));
    }

    public long getWhaleBuyCount(String symbol) {
        return getRecentTrades(symbol).stream()
                .filter(t -> !t.isBuyerMaker() && classifyTradeLevel(t) == 11)
                .count();
    }

    public long getWhaleSellCount(String symbol) {
        return getRecentTrades(symbol).stream()
                .filter(t -> t.isBuyerMaker() && classifyTradeLevel(t) == 11)
                .count();
    }

    public boolean hasRecentVolatility(String symbol, double thresholdUSD) {
        double buyVolume = getRecentTrades(symbol).stream()
                .filter(t -> !t.isBuyerMaker())
                .mapToDouble(t -> t.getPrice() * t.getQuantity())
                .sum();

        double sellVolume = getRecentTrades(symbol).stream()
                .filter(BinanceAggTradeResDto::isBuyerMaker)
                .mapToDouble(t -> t.getPrice() * t.getQuantity())
                .sum();

        return Math.abs(buyVolume - sellVolume) > thresholdUSD;
    }

    // Scheduled Redis cleanup at 8:00 AM Asia/Seoul
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
    public void clearYesterdayTrades() {
        for (String symbol : List.of("btcusdt")) { // Add other symbols if needed
            redisTemplate.delete("trades:" + symbol.toLowerCase());
            redisTemplate.delete("analysis:" + symbol.toLowerCase());
        }
    }

    @Scheduled(fixedDelay = 5000) // 5초마다 실행
    public void scheduledAnalysisTrigger() {
        List<String> symbols = List.of("btcusdt"); // 여기에 원하는 심볼 추가
        for (String symbol : symbols) {
            List<BinanceAggTradeResDto> recentTrades = getRecentTrades(symbol);
            if (recentTrades.size() >= 10) {
                analyzeIfNeeded(symbol);
            }
        }
    }

    public void publishOnly(String symbol, BinanceAggTradeResDto trade) {
        try {
            String json = objectMapper.writeValueAsString(trade);
            tradePublisher.publish(symbol, json);
            log.info("[✅ 실시간 전송] {} 거래 데이터 전송 완료", symbol);
        } catch (JsonProcessingException e) {
            log.error("[❌ 실시간 전송 실패] {}: {}", symbol, e.getMessage(), e);
        }
    }
}