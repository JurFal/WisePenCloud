package com.oriole.wisepen.resource.domain.dto.res;

import com.oriole.wisepen.resource.domain.base.ResourceItemInfoBase;
import com.oriole.wisepen.resource.enums.ResourceAction;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class ResourceItemResponse extends ResourceItemInfoBase {
    private String resourceId;
    private UserDisplayBase ownerInfo;

    private List<ResourceTagBindResponse> tagBinds;
    private List<ResourceAction> currentActions;

    private Map<String, List<ResourceAction>> overrideGrantedActions;
    private Map<String, List<ResourceAction>> specifiedUsersGrantedActions;

    private Map<String, MarketSaleInfoResponse> marketSaleInfos = new HashMap<>();
}
