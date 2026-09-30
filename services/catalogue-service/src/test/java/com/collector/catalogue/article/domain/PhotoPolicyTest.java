package com.collector.catalogue.article.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.collector.catalogue.shared.domain.InvalidRequestException;

class PhotoPolicyTest {

    @Test
    void acceptsJpegPngWebpUpToFiveMegabytes() {
        assertThat(PhotoPolicy.isAcceptable("image/jpeg", 1)).isTrue();
        assertThat(PhotoPolicy.isAcceptable("image/png", PhotoPolicy.MAX_BYTES)).isTrue();
        assertThat(PhotoPolicy.isAcceptable("IMAGE/WEBP", 1000)).isTrue();
    }

    @Test
    void refusesOtherTypesEmptyAndOversizedFiles() {
        assertThat(PhotoPolicy.isAcceptable("image/gif", 1000)).isFalse();
        assertThat(PhotoPolicy.isAcceptable("application/pdf", 1000)).isFalse();
        assertThat(PhotoPolicy.isAcceptable(null, 1000)).isFalse();
        assertThat(PhotoPolicy.isAcceptable("image/png", 0)).isFalse();
        assertThat(PhotoPolicy.isAcceptable("image/png", PhotoPolicy.MAX_BYTES + 1)).isFalse();
    }

    @Test
    void requireAcceptableThrowsAnInvalidRequest() {
        assertThatThrownBy(() -> PhotoPolicy.requireAcceptable("image/gif", 10))
                .isInstanceOf(InvalidRequestException.class);
        PhotoPolicy.requireAcceptable("image/jpeg", 10);
    }

    @Test
    void pendingPhotoGetsAServerChosenKey() {
        var articleId = java.util.UUID.randomUUID();

        Photo photo = Photo.pending(articleId, "image/jpeg", 10);

        assertThat(photo.storageKey()).isEqualTo("articles/" + articleId + "/" + photo.id());
        assertThat(photo.isValidated()).isFalse();
        assertThat(photo.validated().isValidated()).isTrue();
    }
}
