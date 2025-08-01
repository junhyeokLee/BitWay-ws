package com.example.bitwayws.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WhaleTradeResDto {
    private String side; // "매수" or "매도"
    private double quantity;
    private double price;
    private double total;
    private String timestamp;
}