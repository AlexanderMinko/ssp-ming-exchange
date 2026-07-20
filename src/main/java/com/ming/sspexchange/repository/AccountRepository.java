package com.ming.sspexchange.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.ming.sspexchange.model.entity.AccountEntity;

public interface AccountRepository extends MongoRepository<AccountEntity, String> { }
