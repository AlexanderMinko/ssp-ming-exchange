package com.ming.sspexchange.service.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import com.ming.sspexchange.model.openrtb.Bid;
import com.ming.sspexchange.model.openrtb.BidRequest;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface OrtbMapper {

    // Copy of the inbound request with only tmax overridden. Nested beans (app/site/device/...)
    // are shared by reference (identical source/target types -> direct assignment); collections
    // are re-wrapped with the same element instances. Safe: read-only downstream of the copy.
    @Mapping(target = "tmax", source = "newTmax")
    BidRequest copyWithTmax(BidRequest source, Integer newTmax);

    // Flat copy so macro substitution never mutates the bidder's original response object.
    Bid copy(Bid source);
}
