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
public class BidRequest {
    private String id;
    private List<Imp> imp;
    private App app;
    private Site site;
    private Device device;
    private User user;
    private Source source;
    private Regs regs;
    private Integer tmax;
    private List<String> cur;
    private Map<String, Object> ext;
}
