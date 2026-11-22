package com.ming.sspexchange;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;

@SpringBootApplication
@EnableScheduling
public class SspExchangeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SspExchangeApplication.class, args);
    }

    /**
     * Hands Boot's OpenTelemetry SDK to the Logback OTel appender, so log records go over OTLP to the local SigNoz.
     * Boot does not install the appender itself. Lines logged before this runs are buffered by the appender and sent
     * once installed. Deliberately unconditional: install() only wires OTEL appenders that already exist, and
     * logback-spring.xml declares one under the local profile only. That XML is the single switch; on EKS this is a
     * no-op and the SigNoz agent tails stdout instead.
     */
    @Bean
    InitializingBean otlpLogAppenderInstaller(OpenTelemetry openTelemetry) {
        return () -> OpenTelemetryAppender.install(openTelemetry);
    }

}
