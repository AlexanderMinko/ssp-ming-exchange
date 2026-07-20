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
public class Video {
    private List<String> mimes;
    private Integer minduration;
    private Integer maxduration;
    private Integer w;
    private Integer h;
    private List<Integer> protocols;
    private Map<String, Object> ext;
}
