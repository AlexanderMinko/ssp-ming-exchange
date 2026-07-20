package com.ming.sspexchange.model.entity;

import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Targeting {
    private Set<AdFormat> formats;
    private Set<String> countries;   // ISO alpha-3; empty/null = all countries
}
