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
public class Geo {
    private Double lat;
    private Double lon;
    private String country;   // ISO-3166-1 alpha-3, OpenRTB §3.2.19
    private String region;
    private String city;
    private Integer type;
    private Map<String, Object> ext;
}
