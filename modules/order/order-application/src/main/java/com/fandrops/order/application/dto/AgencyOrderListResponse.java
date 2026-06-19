package com.fandrops.order.application.dto;

import java.util.List;

public record AgencyOrderListResponse(List<AgencyOrderListItemResponse> items, Long nextCursor) {}
