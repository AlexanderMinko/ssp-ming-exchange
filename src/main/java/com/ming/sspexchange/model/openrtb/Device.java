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
public class Device {
    private String ua;
    private String ip;
    private Geo geo;
    private String os;
    private String osv;
    private Integer devicetype;
    private String ifa;
    private Map<String, Object> ext;
}
