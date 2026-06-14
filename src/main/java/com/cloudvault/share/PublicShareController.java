package com.cloudvault.share;

import com.cloudvault.storage.PresignedStorageUrl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class PublicShareController {

    private final ShareLinkService shareLinkService;

    public PublicShareController(ShareLinkService shareLinkService) {
        this.shareLinkService = shareLinkService;
    }

    @GetMapping("/s/{token}")
    public ResponseEntity<Void> download(@PathVariable String token) {
        PresignedStorageUrl url = shareLinkService.resolve(token);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(url.url()).toString())
                .build();
    }
}
