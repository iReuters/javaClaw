package com.javaclaw.agent.tools;

import java.util.HashMap;
import java.util.Map;

/**
 * 天气查询工具：返回模拟的天气数据
 */
public class WeatherTool extends BaseTool {

    @Override
    public String getName() {
        return "get_weather";
    }

    @Override
    public String getDescription() {
        return "查询指定城市的天气信息";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> parameters = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> citySchema = new HashMap<>();
        citySchema.put("type", "string");
        citySchema.put("description", "城市名称，例如：北京、上海、广州");
        properties.put("city", citySchema);
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", new String[]{"city"});
        return parameters;
    }

    @Override
    public String execute(Map<String, Object> params) {
        String city = (String) params.get("city");
        if (city == null || city.isEmpty()) {
            return "错误：请提供城市名称";
        }

        // 模拟天气数据
        Map<String, Object> weatherData = new HashMap<>();
        weatherData.put("city", city);
        weatherData.put("temperature", 25);
        weatherData.put("humidity", 60);
        weatherData.put("wind_speed", 10);
        weatherData.put("condition", "晴转多云");
        weatherData.put("pressure", 1013);
        weatherData.put("visibility", 10);

        // 构建返回结果
        StringBuilder result = new StringBuilder();
        result.append("城市：").append(weatherData.get("city")).append("\n");
        result.append("温度：").append(weatherData.get("temperature")).append("°C\n");
        result.append("湿度：").append(weatherData.get("humidity")).append("%\n");
        result.append("风速：").append(weatherData.get("wind_speed")).append(" km/h\n");
        result.append("天气状况：").append(weatherData.get("condition")).append("\n");
        result.append("气压：").append(weatherData.get("pressure")).append(" hPa\n");
        result.append("能见度：").append(weatherData.get("visibility")).append(" km");

        return result.toString();
    }
}