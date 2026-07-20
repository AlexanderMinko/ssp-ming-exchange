package com.ming.sspexchange.service.supply;

import com.ming.sspexchange.model.entity.AccountEntity;
import com.ming.sspexchange.model.entity.PublisherEntity;

public record SupplyContext(AccountEntity account, PublisherEntity publisher) { }
