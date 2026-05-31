package com.eagleeye.domain.dto;

import com.eagleeye.domain.entity.RightType;

/**
 * One callsAndPuts row: the full 12-column {@link PositionDto} plus its call/put marker.
 */
public record OptionsCallPutDto(PositionDto position, RightType rightType) {}
