package com.javaclaw.config;

import lombok.Data;

/**
 * Gateway 服务配置：host、port。对应 config.json 中 gateway。
 */
@Data
public class GatewayConfig {

    private String host = "0.0.0.0";
    private int port = 8765;
}
