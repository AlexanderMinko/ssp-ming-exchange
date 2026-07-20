package com.ming.sspexchange.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.ming.sspexchange.model.entity.PublisherEntity;

public interface PublisherRepository extends MongoRepository<PublisherEntity, String> { }
