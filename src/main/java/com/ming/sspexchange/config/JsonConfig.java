package com.ming.sspexchange.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters.ServerBuilder;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.dslplatform.json.DslJson;
import com.dslplatform.json.runtime.Settings;

/** dsl-json on both sides of the exchange: the inbound controller and the outbound bidder client. */
@Configuration
public class JsonConfig implements WebMvcConfigurer {

    @Bean
    public DslJson<Object> dslJson() {
        // skipDefaultValues is write-only: omits null/default fields from serialized JSON;
        // deserialization is unaffected. oRTB treats absent and null identically.
        return new DslJson<>(Settings.withRuntime()
                .allowArrayFormat(true)
                .skipDefaultValues(true)
                .includeServiceLoader());
    }

    @Override
    public void configureMessageConverters(ServerBuilder builder) {
        builder.addCustomConverter(new DslJsonHttpMessageConverter(dslJson()));
    }
}
