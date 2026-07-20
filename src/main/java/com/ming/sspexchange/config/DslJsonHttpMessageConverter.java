package com.ming.sspexchange.config;

import java.io.IOException;
import java.lang.reflect.Type;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

import com.dslplatform.json.DslJson;

public class DslJsonHttpMessageConverter extends AbstractHttpMessageConverter<Object> {

    private final DslJson<Object> dslJson;

    public DslJsonHttpMessageConverter(DslJson<Object> dslJson) {
        super(MediaType.APPLICATION_JSON);
        this.dslJson = dslJson;
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return dslJson.canSerialize(clazz) || dslJson.canDeserialize(clazz);
    }

    @Override
    public boolean canRead(Class<?> clazz, MediaType mediaType) {
        return canRead(mediaType) && dslJson.canDeserialize(clazz);
    }

    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
        return canWrite(mediaType) && dslJson.canSerialize(clazz);
    }

    @Override
    protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage) throws HttpMessageNotReadableException {
        try {
            return dslJson.deserialize((Type) clazz, inputMessage.getBody());
        } catch (IOException e) {
            throw new HttpMessageNotReadableException("Could not read JSON: %s".formatted(e.getMessage()), e, inputMessage);
        }
    }

    @Override
    protected void writeInternal(Object object, HttpOutputMessage outputMessage) throws IOException,
            HttpMessageNotWritableException {
        dslJson.serialize(object, outputMessage.getBody());
    }
}
