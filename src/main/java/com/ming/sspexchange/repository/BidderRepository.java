package com.ming.sspexchange.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.ming.sspexchange.model.entity.BidderEntity;

public interface BidderRepository extends MongoRepository<BidderEntity, String> { }
