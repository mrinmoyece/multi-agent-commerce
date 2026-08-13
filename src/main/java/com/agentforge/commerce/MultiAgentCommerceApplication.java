package com.agentforge.commerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Multi-Agent E-Commerce Operations Platform
 *
 * Coordinator + 3 specialist agents (Inventory, Pricing, Sentiment)
 * running in parallel, synthesising a daily executive briefing at 06:00.
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties
public class MultiAgentCommerceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MultiAgentCommerceApplication.class, args);
    }
}
