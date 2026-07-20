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
public class Regs {
    private Integer coppa;
    private Integer gdpr;
    private Map<String, Object> ext;
}
