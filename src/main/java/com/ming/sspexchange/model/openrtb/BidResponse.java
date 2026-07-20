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
public class BidResponse {
    private String id;
    private List<SeatBid> seatbid;
    private String bidid;
    private String cur;
    private Integer nbr;
    private Map<String, Object> ext;
}
