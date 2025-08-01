package com.example.bitwayws.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class TradeAnalysisLogResDto {
    private String symbol;
    private String latestTradeTime;
    private Map<Integer, Long> tradeLevels;
    private List<WhaleTradeResDto> whaleTrades;
    private Double buyVolume;
    private Double sellVolume;
    private Double diffVolume;
    private boolean volatilityDetected;
}