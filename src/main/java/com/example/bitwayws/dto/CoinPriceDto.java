package com.example.bitwayws.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CoinPriceDto {
    private String symbol;              // 예: BTC
    private double price;              // 시세
    private String exchange;           // 예: Upbit, Binance
}
