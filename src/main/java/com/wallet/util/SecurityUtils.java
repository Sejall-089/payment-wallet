package com.wallet.util;

import org.springframework.security.core.context.SecurityContextHolder;
import java.util.UUID;

public class SecurityUtils {

    // extracts the logged-in user's UUID from the security context
    // the JWT filter set this — so if we reach here, token was valid
    public static UUID getCurrentUserId() {
        return (UUID) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
    }
}