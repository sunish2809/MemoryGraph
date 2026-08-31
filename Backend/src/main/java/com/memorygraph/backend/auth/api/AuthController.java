package com.memorygraph.backend.auth.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.memorygraph.backend.auth.api.dto.AuthenticationResponse;
import com.memorygraph.backend.auth.api.dto.LoginRequest;
import com.memorygraph.backend.auth.api.dto.RegisterRequest;
import com.memorygraph.backend.auth.api.dto.RegistrationOptionsResponse;
import com.memorygraph.backend.auth.api.dto.UserResponse;
import com.memorygraph.backend.auth.application.AuthService;
import com.memorygraph.backend.auth.security.CurrentUser;
import com.memorygraph.backend.common.api.ApiPaths;
import com.memorygraph.backend.common.api.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(ApiPaths.V1 + "/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/registration")
    public ApiResponse<RegistrationOptionsResponse> registration() {
        return ApiResponse.success(authService.registrationOptions());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthenticationResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthenticationResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me() {
        return ApiResponse.success(authService.currentUser(CurrentUser.requireId()));
    }
}
