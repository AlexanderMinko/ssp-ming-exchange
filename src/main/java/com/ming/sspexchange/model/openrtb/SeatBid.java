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
public class SeatBid {
    private List<Bid> bid;
    private String seat;
    private Integer group;
    private Map<String, Object> ext;
}
