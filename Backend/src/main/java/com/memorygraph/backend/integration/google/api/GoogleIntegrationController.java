package com.memorygraph.backend.integration.google.api;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.memorygraph.backend.auth.security.CurrentUser;
import com.memorygraph.backend.common.api.ApiPaths;
import com.memorygraph.backend.common.api.ApiResponse;
import com.memorygraph.backend.integration.google.GoogleOAuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(ApiPaths.V1 + "/integrations/google")
@Validated
@RequiredArgsConstructor
public class GoogleIntegrationController {

    private final GoogleOAuthService googleOAuth;

    @GetMapping
    public ApiResponse<GoogleIntegrationStatusResponse> status() {
        var userId = CurrentUser.requireId();
        return ApiResponse.success(new GoogleIntegrationStatusResponse(
                googleOAuth.configured(),
                googleOAuth.configured() && googleOAuth.connected(userId)));
    }

    @GetMapping("/authorize")
    public ApiResponse<GoogleAuthorizeResponse> authorize() {
        return ApiResponse.success(new GoogleAuthorizeResponse(
                googleOAuth.authorizationUrl(CurrentUser.requireId())));
    }

    @PostMapping("/callback")
    public ApiResponse<GoogleIntegrationStatusResponse> callback(@Valid @RequestBody GoogleOAuthCallbackRequest body) {
        var userId = CurrentUser.requireId();
        googleOAuth.exchangeCode(userId, body.code().strip(), body.state().strip());
        return ApiResponse.success(new GoogleIntegrationStatusResponse(true, true));
    }

    @DeleteMapping
    public ApiResponse<GoogleIntegrationStatusResponse> disconnect() {
        googleOAuth.disconnect(CurrentUser.requireId());
        return ApiResponse.success(new GoogleIntegrationStatusResponse(googleOAuth.configured(), false));
    }
}
