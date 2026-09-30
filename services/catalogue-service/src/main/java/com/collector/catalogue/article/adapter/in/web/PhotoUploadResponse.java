package com.collector.catalogue.article.adapter.in.web;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.collector.catalogue.article.application.RequestPhotoUpload.PhotoUpload;

record PhotoUploadResponse(UUID photoId, URI uploadUrl, Map<String, String> uploadHeaders, Instant expiresAt) {

    static PhotoUploadResponse from(PhotoUpload upload) {
        return new PhotoUploadResponse(upload.photoId(), upload.upload().url(),
                upload.upload().headers(), upload.upload().expiresAt());
    }
}
