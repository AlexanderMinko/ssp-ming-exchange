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
public class Imp {
    private String id;
    private Banner banner;
    private Video video;
    private Double bidfloor;
    private String bidfloorcur;
    private String tagid;
    private Integer secure;
    private Map<String, Object> ext;
}
