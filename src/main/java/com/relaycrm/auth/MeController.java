package com.relaycrm.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lets a caller see who the API thinks they are. */
@RestController
public class MeController {

    @GetMapping("/api/me")
    public CurrentUser me() {
        return CurrentUserContext.require();
    }
}
