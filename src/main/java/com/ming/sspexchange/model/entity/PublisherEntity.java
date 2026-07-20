package com.ming.sspexchange.model.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document("publishers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublisherEntity {
    @Id
    private String id;
    private String accountId;
    private String publisherId;
    private String name;
    private PartnerStatus status;
}
