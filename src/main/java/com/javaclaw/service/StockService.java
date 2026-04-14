package com.javaclaw.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 股票查询服务
 */
@Service("stockService")
public class StockService {

    /**
     * 查询股票价格
     */
    public String getStockPrice(String symbol) {
        if (symbol == null || symbol.isEmpty()) {
            return "错误：请提供股票代码";
        }

        Map<String, Object> stockData = getStockData(symbol);
        return formatStockResult(stockData);
    }

    /**
     * 查询股票详情（返回Map，可序列化为JSON）
     */
    public Map<String, Object> getStockInfo(String symbol) {
        if (symbol == null || symbol.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "请提供股票代码");
            return error;
        }
        return getStockData(symbol);
    }

    /**
     * 获取多个股票价格
     */
    public Map<String, Object> getMultipleStocks(String[] symbols) {
        Map<String, Object> results = new HashMap<>();
        for (String symbol : symbols) {
            results.put(symbol, getStockData(symbol));
        }
        return results;
    }

    private Map<String, Object> getStockData(String symbol) {
        // 模拟数据
        double price = 100.0 + (symbol.hashCode() % 100);
        double change = (Math.random() * 20) - 10;

        Map<String, Object> data = new HashMap<>();
        data.put("symbol", symbol.toUpperCase());
        data.put("price", Math.round(price * 100) / 100.0);
        data.put("change", Math.round(change * 100) / 100.0);
        data.put("changePercent", Math.round((change / price) * 10000) / 100.0);
        data.put("currency", "USD");
        data.put("market", "NASDAQ");
        data.put("timestamp", System.currentTimeMillis());
        return data;
    }

    private String formatStockResult(Map<String, Object> data) {
        return String.format(
                "股票：%s\n当前价：%.2f %s\n涨跌：%.2f (%.2f%%)\n市场：%s",
                data.get("symbol"),
                data.get("price"),
                data.get("currency"),
                data.get("change"),
                data.get("changePercent"),
                data.get("market")
        );
    }
}