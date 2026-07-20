package com.ming.sspexchange.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.dslplatform.json.DslJson;
import com.dslplatform.json.runtime.Settings;

@Configuration
public class DslJsonConfig {

    @Bean
    public DslJson<Object> dslJson() {
        // skipDefaultValues is write-only: omits null/default fields from serialized JSON;
        // deserialization is unaffected. oRTB treats absent and null identically.
        return new DslJson<>(Settings.withRuntime()
                .allowArrayFormat(true)
                .skipDefaultValues(true)
                .includeServiceLoader());
    }
}
