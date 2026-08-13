package com.agentforge.commerce.runtime.tools.live.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge.commerce.runtime.tools.live.InventoryBackend;
import com.agentforge.commerce.runtime.tools.live.PricingBackend;
import com.agentforge.commerce.runtime.tools.live.SentimentBackend;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Verifies the LIVE wiring in {@link LiveAdapterConfig}: it only activates under
 * {@code runtime.tools.mode=LIVE}, it creates the three backend beans when every
 * adapter is fully configured, and it fails application startup fast when any
 * adapter configuration is unsafe or incomplete.
 */
class LiveAdapterConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withUserConfiguration(TestClockConfig.class, LiveAdapterConfig.class);

    private static String[] validProps() {
        return new String[] {
            "runtime.tools.mode=LIVE",
            "runtime.tools.live.inventory.base-url=https://wms.example.com",
            "runtime.tools.live.inventory.path=/api/v1/inventory",
            "runtime.tools.live.inventory.allowed-hosts[0]=wms.example.com",
            "runtime.tools.live.inventory.auth.type=API_KEY",
            "runtime.tools.live.inventory.auth.api-key-header=X-Api-Key",
            "runtime.tools.live.inventory.auth.api-key-value=k",
            "runtime.tools.live.pricing.base-url=https://pricing.example.com",
            "runtime.tools.live.pricing.path=/api/v1/pricing",
            "runtime.tools.live.pricing.allowed-hosts[0]=pricing.example.com",
            "runtime.tools.live.pricing.auth.type=API_KEY",
            "runtime.tools.live.pricing.auth.api-key-header=X-Api-Key",
            "runtime.tools.live.pricing.auth.api-key-value=k",
            "runtime.tools.live.sentiment.base-url=https://sentiment.example.com",
            "runtime.tools.live.sentiment.path=/api/v1/sentiment",
            "runtime.tools.live.sentiment.allowed-hosts[0]=sentiment.example.com",
            "runtime.tools.live.sentiment.auth.type=API_KEY",
            "runtime.tools.live.sentiment.auth.api-key-header=X-Api-Key",
            "runtime.tools.live.sentiment.auth.api-key-value=k",
        };
    }

    @Test
    void doesNotActivateOutsideLiveMode() {
        runner.withPropertyValues("runtime.tools.mode=FIXTURE").run(context ->
                assertThat(context).doesNotHaveBean(InventoryBackend.class));
    }

    @Test
    void wiresAllThreeBackendsWhenFullyConfigured() {
        runner.withPropertyValues(validProps()).run(context -> {
            assertThat(context).hasSingleBean(InventoryBackend.class);
            assertThat(context).hasSingleBean(PricingBackend.class);
            assertThat(context).hasSingleBean(SentimentBackend.class);
            assertThat(context.getBean(InventoryBackend.class).systemOfRecord()).isNotBlank();
        });
    }

    @Test
    void failsStartupWhenAnAdapterHasPlaintextUrl() {
        String[] props = validProps();
        props[1] = "runtime.tools.live.inventory.base-url=http://wms.example.com";
        runner.withPropertyValues(props).run(context ->
                assertThat(context).hasFailed());
    }

    @Test
    void failsStartupWhenAuthIncomplete() {
        String[] props = validProps();
        props[6] = "runtime.tools.live.inventory.auth.api-key-value=";
        runner.withPropertyValues(props).run(context ->
                assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    static class TestClockConfig {
        @Bean
        Clock runtimeClock() {
            return Clock.systemUTC().withZone(ZoneOffset.UTC);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().registerModule(new JavaTimeModule());
        }
    }
}
