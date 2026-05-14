package com.inkwell.media.service.impl;

import com.inkwell.media.dto.MediaResponse;
import com.inkwell.media.entity.Media;
import com.inkwell.media.exception.CustomException;
import com.inkwell.media.repository.MediaRepository;
import com.inkwell.media.service.MediaService;
import com.inkwell.media.util.FileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaServiceImpl implements MediaService {

    private final MediaRepository mediaRepository;
    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Value("${aws.region}")
    private String region;

    @Value("${media.local-path}")
    private String localPath;

    @Override
    @Transactional
    public MediaResponse uploadMedia(MultipartFile file, Long uploaderId) {
        if (file.isEmpty()) {
            throw new CustomException("File is empty", HttpStatus.BAD_REQUEST);
        }

        if (!FileUtil.isValidFileType(file.getContentType())) {
            throw new CustomException(
                "Invalid file type: " + file.getContentType() + ". Allowed: " + FileUtil.getAllowedTypesDescription(),
                HttpStatus.BAD_REQUEST
            );
        }

        if (!FileUtil.isValidFileSize(file.getSize())) {
            throw new CustomException("File size exceeds 10MB limit", HttpStatus.BAD_REQUEST);
        }

        String filename = FileUtil.generateUniqueFilename(file.getOriginalFilename());
        
        if (bucketName != null && !bucketName.isEmpty() && !bucketName.equals("NOT_SET") && !bucketName.contains("AWS_BUCKET_NAME")) {
            return uploadToS3(file, filename, uploaderId);
        } else {
            return uploadToLocal(file, filename, uploaderId);
        }
    }

    private MediaResponse uploadToS3(MultipartFile file, String filename, Long uploaderId) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(filename)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            
            String url = String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, filename);

            return saveMediaEntity(file, filename, url, uploaderId);

        } catch (Exception e) {
            // Catches both IOException and AWS SDK runtime exceptions (SdkClientException,
            // AwsServiceException, etc.) that occur when credentials are invalid / bucket
            // does not exist.  Fall back to local disk storage instead of returning 500.
            log.error("Failed to upload file to S3 ({}), falling back to local storage", e.getMessage());
            return uploadToLocal(file, filename, uploaderId);
        }
    }

    private MediaResponse uploadToLocal(MultipartFile file, String filename, Long uploaderId) {
        try {
            Path root = Paths.get(localPath).toAbsolutePath().normalize();
            log.info("Attempting to save file {} to local storage at: {}", filename, root);
            
            if (!Files.exists(root)) {
                Files.createDirectories(root);
                log.info("Created local uploads directory at: {}", root);
            }
            
            Files.copy(file.getInputStream(), root.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            log.info("Successfully saved file {} to {}", filename, root);
            
            // Assuming the media service serves these files under /uploads
            String url = "/api/v1/media/files/" + filename;

            return saveMediaEntity(file, filename, url, uploaderId);
        } catch (IOException e) {
            log.error("Failed to upload file to local storage. Path: {}, Error: {}", localPath, e.getMessage(), e);
            throw new CustomException("File upload failed: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private MediaResponse saveMediaEntity(MultipartFile file, String filename, String url, Long uploaderId) {
        Media media = Media.builder()
                .uploaderId(uploaderId)
                .filename(filename)
                .originalName(file.getOriginalFilename())
                .url(url)
                .mimeType(file.getContentType())
                .sizeKb(file.getSize() / 1024)
                .isDeleted(false)
                .build();

        return mapToMediaResponse(mediaRepository.save(media));
    }

    @Override
    public MediaResponse getMediaById(Long mediaId) {
        Media media = mediaRepository.findByMediaId(mediaId)
                .orElseThrow(() -> new CustomException("Media not found", HttpStatus.NOT_FOUND));
        if (media.isDeleted()) {
            throw new CustomException("Media has been deleted", HttpStatus.GONE);
        }
        return mapToMediaResponse(media);
    }

    @Override
    public List<MediaResponse> getMediaByUploader(Long uploaderId) {
        return mediaRepository.findByUploaderId(uploaderId).stream()
                .filter(media -> !media.isDeleted())
                .map(this::mapToMediaResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<MediaResponse> getMediaByPost(Long postId) {
        return mediaRepository.findByLinkedPostId(postId).stream()
                .filter(media -> !media.isDeleted())
                .map(this::mapToMediaResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteMedia(Long mediaId) {
        Media media = mediaRepository.findByMediaId(mediaId)
                .orElseThrow(() -> new CustomException("Media not found", HttpStatus.NOT_FOUND));
        media.setDeleted(true);
        mediaRepository.save(media);
    }

    @Override
    @Transactional
    public MediaResponse updateAltText(Long mediaId, String altText) {
        Media media = mediaRepository.findByMediaId(mediaId)
                .orElseThrow(() -> new CustomException("Media not found", HttpStatus.NOT_FOUND));
        media.setAltText(altText);
        return mapToMediaResponse(mediaRepository.save(media));
    }

    @Override
    @Transactional
    public MediaResponse linkToPost(Long mediaId, Long postId) {
        Media media = mediaRepository.findByMediaId(mediaId)
                .orElseThrow(() -> new CustomException("Media not found", HttpStatus.NOT_FOUND));
        
        if (media.getLinkedPostId() != null && !media.getLinkedPostId().equals(postId)) {
            throw new CustomException("Media is already linked to another post. Unlink it first.", HttpStatus.BAD_REQUEST);
        }
        
        media.setLinkedPostId(postId);
        return mapToMediaResponse(mediaRepository.save(media));
    }

    @Override
    @Transactional
    public MediaResponse unlinkFromPost(Long mediaId) {
        Media media = mediaRepository.findByMediaId(mediaId)
                .orElseThrow(() -> new CustomException("Media not found", HttpStatus.NOT_FOUND));
        media.setLinkedPostId(null);
        return mapToMediaResponse(mediaRepository.save(media));
    }

    @Override
    public List<MediaResponse> getAllMedia() {
        return mediaRepository.findByIsDeleted(false).stream()
                .map(this::mapToMediaResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @Scheduled(cron = "0 0 * * * ?") // Every hour
    public void cleanupDeleted() {
        List<Media> deletedMedia = mediaRepository.findByIsDeleted(true);
        for (Media media : deletedMedia) {
            if (media.getUrl().contains("amazonaws.com")) {
                cleanupFromS3(media);
            } else {
                cleanupFromLocal(media);
            }
        }
    }

    private void cleanupFromS3(Media media) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(media.getFilename())
                    .build();
            s3Client.deleteObject(deleteObjectRequest);
            mediaRepository.delete(media);
        } catch (Exception e) {
            log.error("Failed to cleanup media from S3: {}", media.getFilename(), e);
        }
    }

    private void cleanupFromLocal(Media media) {
        try {
            Path file = Paths.get(localPath).resolve(media.getFilename());
            Files.deleteIfExists(file);
            mediaRepository.delete(media);
        } catch (IOException e) {
            log.error("Failed to cleanup media from local storage: {}", media.getFilename(), e);
        }
    }

    private MediaResponse mapToMediaResponse(Media media) {
        return MediaResponse.builder()
                .mediaId(media.getMediaId())
                .uploaderId(media.getUploaderId())
                .filename(media.getFilename())
                .originalName(media.getOriginalName())
                .url(media.getUrl())
                .mimeType(media.getMimeType())
                .sizeKb(media.getSizeKb())
                .altText(media.getAltText())
                .linkedPostId(media.getLinkedPostId())
                .isDeleted(media.isDeleted())
                .uploadedAt(media.getUploadedAt())
                .build();
    }
}
