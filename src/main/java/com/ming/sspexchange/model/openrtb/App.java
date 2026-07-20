package com.ming.sspexchange.model.openrtb;

import java.util.Map;

import com.dslplatform.json.CompiledJson;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@CompiledJson
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class App {
    private String id;
    private String name;
    private String bundle;
    private String storeurl;
    private Publisher publisher;
    private Map<String, Object> ext;
}
