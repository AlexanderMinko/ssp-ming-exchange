package com.ming.sspexchange.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters.ServerBuilder;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.dslplatform.json.DslJson;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final DslJson<Object> dslJson;

    @Override
    public void configureMessageConverters(ServerBuilder builder) {
        builder.addCustomConverter(new DslJsonHttpMessageConverter(dslJson));
    }
}
