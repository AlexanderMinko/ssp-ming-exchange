package com.ming.sspexchange.model.openrtb;

import java.util.List;
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
public class Bid {
    private String id;
    private String impid;
    private Double price;
    private String adm;
    private String nurl;
    private String adid;
    private List<String> adomain;
    private String cid;
    private String crid;
    private Integer w;
    private Integer h;
    private Integer mtype;
    private Map<String, Object> ext;
}
