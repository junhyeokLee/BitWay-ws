package com.example.bitwayws.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class BinanceAggTradeResDto {
    @JsonProperty("a")
    private long aggTradeId;

    @JsonProperty("p")
    private double price;

    @JsonProperty("q")
    private double quantity;

    @JsonProperty("T")
    private long timestamp;

    @JsonProperty("m")
    private boolean isBuyerMaker;

    @JsonProperty("s")
    private String symbol;
}