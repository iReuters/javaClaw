package com.javaclaw.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 天气查询服务
 */
@Service("weatherService")
public class WeatherService {

    /**
     * 查询天气（单参数）
     */
    public String getWeather(String city) {
        if (city == null || city.isEmpty()) {
            return "错误：请提供城市名称";
        }

        Map<String, Object> weatherData = getWeatherData(city);
        return formatWeatherResult(weatherData);
    }

    /**
     * 查询天气详情（返回Map）
     */
    public Map<String, Object> getWeatherInfo(String city) {
        if (city == null || city.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "请提供城市名称");
            return error;
        }
        return getWeatherData(city);
    }

    private Map<String, Object> getWeatherData(String city) {
        // 模拟数据
        int temp = 20 + (city.hashCode() % 15);
        int humidity = 50 + (city.hashCode() % 40);
        String[] conditions = {"晴", "多云", "阴", "小雨", "晴转多云"};
        int conditionIdx = Math.abs(city.hashCode()) % conditions.length;

        Map<String, Object> data = new HashMap<>();
        data.put("city", city);
        data.put("temperature", temp);
        data.put("humidity", humidity);
        data.put("condition", conditions[conditionIdx]);
        data.put("windSpeed", 10 + (Math.abs(city.hashCode()) % 20));
        data.put("airQuality", new String[]{"优", "良", "中等"}[Math.abs(city.hashCode()) % 3]);
        return data;
    }

    private String formatWeatherResult(Map<String, Object> data) {
        return String.format(
                "【%s 天气】\n温度：%d°C\n天气状况：%s\n湿度：%d%%\n风速：%d km/h\n空气质量：%s",
                data.get("city"),
                data.get("temperature"),
                data.get("condition"),
                data.get("humidity"),
                data.get("windSpeed"),
                data.get("airQuality")
        );
    }
}