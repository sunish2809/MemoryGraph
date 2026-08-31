package com.memorygraph.backend.account.api;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.memorygraph.backend.account.api.dto.DeleteAccountRequest;
import com.memorygraph.backend.account.api.dto.PrivacyStatusResponse;
import com.memorygraph.backend.account.application.AccountService;
import com.memorygraph.backend.auth.security.CurrentUser;
import com.memorygraph.backend.common.api.ApiPaths;
import com.memorygraph.backend.common.api.ApiResponse;
import com.memorygraph.backend.common.error.ApiException;
import com.memorygraph.backend.common.error.ErrorCode;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(ApiPaths.V1 + "/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/privacy")
    public ApiResponse<PrivacyStatusResponse> privacy() {
        return ApiResponse.success(accountService.privacy());
    }

    /**
     * A zip of {@code archive.json} plus media files. Not wrapped in the JSON envelope — same
     * pattern as media downloads.
     */
    @GetMapping(value = "/export", produces = "application/zip")
    public void export(HttpServletResponse response) {
        String filename = "memorygraph-archive-" + LocalDate.now(ZoneOffset.UTC) + ".zip";
        response.setContentType("application/zip");
        response.setHeader(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(filename, java.nio.charset.StandardCharsets.UTF_8).build()
                        .toString());
        try {
            accountService.exportArchive(CurrentUser.requireId(), response.getOutputStream());
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Could not write the archive", ex);
        }
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Valid @RequestBody DeleteAccountRequest request) {
        accountService.deleteAccount(CurrentUser.requireId(), request.password(), request.confirmation());
    }
}
