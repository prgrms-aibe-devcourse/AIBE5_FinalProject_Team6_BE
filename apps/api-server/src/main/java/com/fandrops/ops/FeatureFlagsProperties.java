package com.fandrops.ops;

import org.springframework.boot.context.properties.ConfigurationProperties;
/**
 * Feature flag — 현재 미사용. 도입 시 이 설정으로 on/off.
 */
@ConfigurationProperties(prefix = "fandrops.feature-flags")
public class FeatureFlagsProperties {

    private boolean enabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
