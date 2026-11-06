package com.ming.sspexchange.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.ming.sspexchange.model.entity.SupplyPartner;

public interface SupplyPartnerRepository extends MongoRepository<SupplyPartner, String> { }
