package org.opentron.backend.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * NetworkPolicyService - central source of truth for whether outbound internet
 * access to cloud providers is allowed. Reads default from configuration and
 * allows runtime override via setter (used by SettingsController).
 */
@Service
public class NetworkPolicyService {

    private volatile boolean allowInternet;

    public NetworkPolicyService(@Value("${security.allow_internet:true}") boolean allowInternet) {
        this.allowInternet = allowInternet;
    }

    public boolean isInternetAllowed() {
        return allowInternet;
    }

    public void setAllowInternet(boolean allowInternet) {
        this.allowInternet = allowInternet;
    }
}
