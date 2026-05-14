package com.inkwell.media.service.impl;

import com.inkwell.media.dto.MediaResponse;
import com.inkwell.media.entity.Media;
import com.inkwell.media.exception.CustomException;
import com.inkwell.media.repository.MediaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaServiceImplTest {

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private MediaServiceImpl mediaService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mediaService, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(mediaService, "region", "us-east-1");
        ReflectionTestUtils.setField(mediaService, "localPath", tempDir.toString());
    }

    @Test
    void uploadMedia_WhenEmpty_ShouldThrowBadRequest() {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", new byte[0]);
        assertThrows(CustomException.class, () -> mediaService.uploadMedia(file, 1L));
    }

    @Test
    void uploadMedia_WhenInvalidType_ShouldThrowBadRequest() {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "data".getBytes());
        assertThrows(CustomException.class, () -> mediaService.uploadMedia(file, 1L));
    }

    @Test
    void uploadMedia_WhenTooLarge_ShouldThrowBadRequest() {
        MockMultipartFile file = new MockMultipartFile("file", "big.jpg", "image/jpeg", new byte[11 * 1024 * 1024]);
        assertThrows(CustomException.class, () -> mediaService.uploadMedia(file, 1L));
    }

    @Test
    void uploadMedia_ToS3_Success() {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "test data".getBytes());
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(PutObjectResponse.builder().build());
        when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MediaResponse response = mediaService.uploadMedia(file, 1L);

        assertTrue(response.getUrl().contains("amazonaws.com"));
    }

    @Test
    void uploadMedia_ToLocal_Fallback() {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "test data".getBytes());
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(new RuntimeException("S3 error"));
        when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MediaResponse response = mediaService.uploadMedia(file, 1L);

        assertTrue(response.getUrl().contains("/api/v1/media/files/"));
    }

    @Test
    void uploadMedia_ToLocal_Directly() {
        ReflectionTestUtils.setField(mediaService, "bucketName", "");
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "test data".getBytes());
        when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MediaResponse response = mediaService.uploadMedia(file, 1L);

        assertTrue(response.getUrl().contains("/api/v1/media/files/"));
    }

    @Test
    void uploadMedia_ToLocal_WhenBucketIsNull() {
        ReflectionTestUtils.setField(mediaService, "bucketName", null);
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "test data".getBytes());
        when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MediaResponse response = mediaService.uploadMedia(file, 1L);

        assertTrue(response.getUrl().contains("/api/v1/media/files/"));
    }

    @Test
    void uploadMedia_ToLocal_WhenBucketIsNotSet() {
        ReflectionTestUtils.setField(mediaService, "bucketName", "NOT_SET");
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "test data".getBytes());
        when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MediaResponse response = mediaService.uploadMedia(file, 1L);

        assertTrue(response.getUrl().contains("/api/v1/media/files/"));
    }

    @Test
    void uploadMedia_ToLocal_WhenBucketContainsPlaceholder() {
        ReflectionTestUtils.setField(mediaService, "bucketName", "AWS_BUCKET_NAME_PLACEHOLDER");
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "test data".getBytes());
        when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MediaResponse response = mediaService.uploadMedia(file, 1L);

        assertTrue(response.getUrl().contains("/api/v1/media/files/"));
    }

    @Test
    void uploadMedia_ToLocal_WhenDirectoryDoesNotExist_ShouldCreateIt() {
        Path nestedDir = tempDir.resolve("uploads").resolve("nested");
        ReflectionTestUtils.setField(mediaService, "bucketName", "");
        ReflectionTestUtils.setField(mediaService, "localPath", nestedDir.toString());
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "test data".getBytes());
        when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MediaResponse response = mediaService.uploadMedia(file, 1L);

        assertTrue(Files.exists(nestedDir));
        assertTrue(response.getUrl().contains("/api/v1/media/files/"));
    }

    @Test
    void uploadMedia_ToLocal_WhenCopyFails_ShouldThrowInternalServerError() throws Exception {
        ReflectionTestUtils.setField(mediaService, "bucketName", "");
        org.springframework.web.multipart.MultipartFile file = org.mockito.Mockito.mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getSize()).thenReturn(10L);
        when(file.getOriginalFilename()).thenReturn("broken.jpg");
        when(file.getInputStream()).thenThrow(new java.io.IOException("copy failed"));

        CustomException exception = assertThrows(CustomException.class, () -> mediaService.uploadMedia(file, 1L));

        assertTrue(exception.getMessage().contains("File upload failed: copy failed"));
    }

    @Test
    void getMediaById_WhenMissing_ShouldThrowNotFound() {
        when(mediaRepository.findByMediaId(1L)).thenReturn(Optional.empty());
        assertThrows(CustomException.class, () -> mediaService.getMediaById(1L));
    }

    @Test
    void getMediaById_WhenDeleted_ShouldThrowGone() {
        when(mediaRepository.findByMediaId(1L)).thenReturn(Optional.of(Media.builder().mediaId(1L).isDeleted(true).build()));
        assertThrows(CustomException.class, () -> mediaService.getMediaById(1L));
    }

    @Test
    void getMediaById_ShouldReturnResponse() {
        when(mediaRepository.findByMediaId(1L)).thenReturn(Optional.of(Media.builder().mediaId(1L).filename("a.jpg").isDeleted(false).build()));
        assertEquals(1L, mediaService.getMediaById(1L).getMediaId());
    }

    @Test
    void getMediaByUploader_ShouldFilterDeleted() {
        when(mediaRepository.findByUploaderId(1L)).thenReturn(List.of(
                Media.builder().mediaId(1L).isDeleted(false).build(),
                Media.builder().mediaId(2L).isDeleted(true).build()
        ));
        assertEquals(1, mediaService.getMediaByUploader(1L).size());
    }

    @Test
    void getMediaByPost_ShouldFilterDeleted() {
        when(mediaRepository.findByLinkedPostId(9L)).thenReturn(List.of(
                Media.builder().mediaId(1L).isDeleted(false).build(),
                Media.builder().mediaId(2L).isDeleted(true).build()
        ));
        assertEquals(1, mediaService.getMediaByPost(9L).size());
    }

    @Test
    void deleteMedia_ShouldMarkDeleted() {
        Media media = Media.builder().mediaId(1L).isDeleted(false).build();
        when(mediaRepository.findByMediaId(1L)).thenReturn(Optional.of(media));

        mediaService.deleteMedia(1L);

        assertTrue(media.isDeleted());
        verify(mediaRepository).save(media);
    }

    @Test
    void deleteMedia_WhenMissing_ShouldThrowNotFound() {
        when(mediaRepository.findByMediaId(1L)).thenReturn(Optional.empty());

        assertThrows(CustomException.class, () -> mediaService.deleteMedia(1L));
    }

    @Test
    void updateAltText_ShouldSave() {
        Media media = Media.builder().mediaId(1L).altText("old").build();
        when(mediaRepository.findByMediaId(1L)).thenReturn(Optional.of(media));
        when(mediaRepository.save(media)).thenReturn(media);

        MediaResponse response = mediaService.updateAltText(1L, "new");

        assertEquals("new", response.getAltText());
    }

    @Test
    void updateAltText_WhenMissing_ShouldThrowNotFound() {
        when(mediaRepository.findByMediaId(1L)).thenReturn(Optional.empty());

        assertThrows(CustomException.class, () -> mediaService.updateAltText(1L, "new"));
    }

    @Test
    void linkToPost_WhenAlreadyLinkedElsewhere_ShouldThrow() {
        Media media = Media.builder().mediaId(1L).linkedPostId(2L).build();
        when(mediaRepository.findByMediaId(1L)).thenReturn(Optional.of(media));
        assertThrows(CustomException.class, () -> mediaService.linkToPost(1L, 3L));
    }

    @Test
    void linkToPost_WhenMissing_ShouldThrowNotFound() {
        when(mediaRepository.findByMediaId(1L)).thenReturn(Optional.empty());

        assertThrows(CustomException.class, () -> mediaService.linkToPost(1L, 3L));
    }

    @Test
    void linkToPost_ShouldSave() {
        Media media = Media.builder().mediaId(1L).linkedPostId(null).build();
        when(mediaRepository.findByMediaId(1L)).thenReturn(Optional.of(media));
        when(mediaRepository.save(media)).thenReturn(media);

        MediaResponse response = mediaService.linkToPost(1L, 3L);

        assertEquals(3L, response.getLinkedPostId());
    }

    @Test
    void linkToPost_WhenAlreadyLinkedToSamePost_ShouldSave() {
        Media media = Media.builder().mediaId(1L).linkedPostId(3L).build();
        when(mediaRepository.findByMediaId(1L)).thenReturn(Optional.of(media));
        when(mediaRepository.save(media)).thenReturn(media);

        MediaResponse response = mediaService.linkToPost(1L, 3L);

        assertEquals(3L, response.getLinkedPostId());
    }

    @Test
    void unlinkFromPost_ShouldClearLink() {
        Media media = Media.builder().mediaId(1L).linkedPostId(3L).build();
        when(mediaRepository.findByMediaId(1L)).thenReturn(Optional.of(media));
        when(mediaRepository.save(media)).thenReturn(media);

        MediaResponse response = mediaService.unlinkFromPost(1L);

        assertEquals(null, response.getLinkedPostId());
    }

    @Test
    void unlinkFromPost_WhenMissing_ShouldThrowNotFound() {
        when(mediaRepository.findByMediaId(1L)).thenReturn(Optional.empty());

        assertThrows(CustomException.class, () -> mediaService.unlinkFromPost(1L));
    }

    @Test
    void getAllMedia_ShouldReturnOnlyNonDeleted() {
        when(mediaRepository.findByIsDeleted(false)).thenReturn(List.of(Media.builder().mediaId(1L).build()));
        assertEquals(1, mediaService.getAllMedia().size());
    }

    @Test
    void cleanupDeleted_FromLocal() throws Exception {
        Path file = tempDir.resolve("test.jpg");
        Files.createFile(file);
        Media media = Media.builder().filename("test.jpg").url("/api/v1/media/files/test.jpg").isDeleted(true).build();
        when(mediaRepository.findByIsDeleted(true)).thenReturn(List.of(media));

        mediaService.cleanupDeleted();

        assertFalse(Files.exists(file));
        verify(mediaRepository).delete(media);
    }

    @Test
    void cleanupDeleted_FromS3() {
        Media media = Media.builder().filename("test.jpg").url("https://test-bucket.s3.amazonaws.com/test.jpg").isDeleted(true).build();
        when(mediaRepository.findByIsDeleted(true)).thenReturn(List.of(media));

        mediaService.cleanupDeleted();

        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
        verify(mediaRepository).delete(media);
    }

    @Test
    void cleanupDeleted_FromS3Failure_ShouldNotDeleteEntity() {
        Media media = Media.builder().filename("test.jpg").url("https://test-bucket.s3.amazonaws.com/test.jpg").isDeleted(true).build();
        when(mediaRepository.findByIsDeleted(true)).thenReturn(List.of(media));
        doThrow(new RuntimeException("boom")).when(s3Client).deleteObject(any(DeleteObjectRequest.class));

        mediaService.cleanupDeleted();

        verify(mediaRepository, never()).delete(media);
    }

    @Test
    void cleanupDeleted_FromLocalMissingFile_ShouldStillDeleteEntity() {
        Media media = Media.builder().filename("missing.jpg").url("/api/v1/media/files/missing.jpg").isDeleted(true).build();
        when(mediaRepository.findByIsDeleted(true)).thenReturn(List.of(media));

        mediaService.cleanupDeleted();

        verify(mediaRepository).delete(media);
    }

    @Test
    void cleanupDeleted_FromLocalDeleteFailure_ShouldKeepEntity() throws Exception {
        Path blockingDir = tempDir.resolve("occupied");
        Files.createDirectories(blockingDir.resolve("child"));
        ReflectionTestUtils.setField(mediaService, "localPath", tempDir.toString());
        Media media = Media.builder().filename("occupied").url("/api/v1/media/files/occupied").isDeleted(true).build();
        when(mediaRepository.findByIsDeleted(true)).thenReturn(List.of(media));

        mediaService.cleanupDeleted();

        verify(mediaRepository, never()).delete(media);
    }
}
