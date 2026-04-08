package com.javaclaw.agent.tools;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 汇率查询工具：返回模拟的汇率数据
 */
public class ExchangeRateTool extends BaseTool {

    @Override
    public String getName() {
        return "get_exchange_rate";
    }

    @Override
    public String getDescription() {
        return "查询货币之间的汇率";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        properties.put("type", "string");
        properties.put("description", "源货币代码，例如 USD");
        properties.put("required", true);
        params.put("from_currency", properties);
        
        properties = new HashMap<>();
        properties.put("type", "string");
        properties.put("description", "目标货币代码，例如 CNY");
        properties.put("required", true);
        params.put("to_currency", properties);
        
        return params;
    }

    @Override
    public String execute(Map<String, Object> params) {
        String fromCurrency = (String) params.get("from_currency");
        String toCurrency = (String) params.get("to_currency");
        
        if (fromCurrency == null || toCurrency == null) {
            return "[Error: Missing required parameters]";
        }
        
        // 模拟汇率数据
        Map<String, Double> exchangeRates = new HashMap<>();
        exchangeRates.put("USD_CNY", 7.25);
        exchangeRates.put("CNY_USD", 0.14);
        exchangeRates.put("USD_EUR", 0.92);
        exchangeRates.put("EUR_USD", 1.09);
        exchangeRates.put("USD_GBP", 0.79);
        exchangeRates.put("GBP_USD", 1.27);
        exchangeRates.put("CNY_EUR", 0.13);
        exchangeRates.put("EUR_CNY", 7.90);
        exchangeRates.put("CNY_GBP", 0.11);
        exchangeRates.put("GBP_CNY", 9.15);
        
        String key = fromCurrency.toUpperCase() + "_" + toCurrency.toUpperCase();
        Double rate = exchangeRates.get(key);
        
        if (rate != null) {
            return String.format("当前汇率：1 %s = %.2f %s", fromCurrency.toUpperCase(), rate, toCurrency.toUpperCase());
        } else {
            return String.format("[Error: 未找到 %s 到 %s 的汇率数据]", fromCurrency.toUpperCase(), toCurrency.toUpperCase());
        }
    }
}