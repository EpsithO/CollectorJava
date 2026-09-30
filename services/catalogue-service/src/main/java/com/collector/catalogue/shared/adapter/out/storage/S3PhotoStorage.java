package com.collector.catalogue.shared.adapter.out.storage;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.collector.catalogue.shared.application.port.PhotoStorage;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
class S3PhotoStorage implements PhotoStorage {

    private final S3Client s3;              // endpoint INTERNE : HEAD, suppression
    private final S3Presigner presigner;    // endpoint PUBLIC : l'URL signée contient l'hôte
    private final StorageProperties props;

    S3PhotoStorage(S3Client s3, S3Presigner presigner, StorageProperties props) {
        this.s3 = s3;
        this.presigner = presigner;
        this.props = props;
    }

    @Override
    public PresignedUpload prepareUpload(String key, String contentType) {
        var presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(props.uploadTtl())
                .putObjectRequest(r -> r.bucket(props.bucket()).key(key).contentType(contentType))
                .build());
        // Le Content-Type est signé : le navigateur doit renvoyer exactement cet en-tête.
        return new PresignedUpload(URI.create(presigned.url().toString()),
                Map.of("Content-Type", contentType), presigned.expiration());
    }

    @Override
    public Optional<StoredObject> stat(String key) {
        try {
            var head = s3.headObject(r -> r.bucket(props.bucket()).key(key));
            return Optional.of(new StoredObject(key, head.contentType(), head.contentLength()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            // HEAD n'a pas de corps : un objet absent remonte parfois en 404 générique.
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public URI downloadUrl(String key) {
        var presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(props.downloadTtl())
                .getObjectRequest(r -> r.bucket(props.bucket()).key(key))
                .build());
        return URI.create(presigned.url().toString());
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(r -> r.bucket(props.bucket()).key(key));
    }
}
