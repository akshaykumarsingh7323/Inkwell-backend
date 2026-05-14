package com.inkwell.media.controller;

import com.inkwell.media.dto.MediaResponse;
import com.inkwell.media.exception.CustomException;
import com.inkwell.media.service.MediaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaControllerTest {

    @Mock
    private MediaService mediaService;

    @InjectMocks
    private MediaController mediaController;

    @TempDir
    Path tempDir;

    @Test
    void upload_WithValidHeader_ShouldReturnCreated() {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "data".getBytes());
        MediaResponse response = new MediaResponse();
        when(mediaService.uploadMedia(file, 1L)).thenReturn(response);

        assertEquals(HttpStatus.CREATED, mediaController.upload(file, "1").getStatusCode());
    }

    @Test
    void upload_WhenHeaderMissing_ShouldReturnUnauthorized() {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "data".getBytes());

        assertEquals(HttpStatus.UNAUTHORIZED, mediaController.upload(file, null).getStatusCode());
    }

    @Test
    void upload_WhenHeaderInvalid_ShouldReturnBadRequest() {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "data".getBytes());

        assertEquals(HttpStatus.BAD_REQUEST, mediaController.upload(file, "bad-id").getStatusCode());
    }

    @Test
    void upload_WhenServiceFails_ShouldReturnInternalServerError() {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "data".getBytes());
        when(mediaService.uploadMedia(file, 1L)).thenThrow(new RuntimeException("boom"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, mediaController.upload(file, "1").getStatusCode());
    }

    @Test
    void getById_ShouldReturnMedia() {
        MediaResponse response = new MediaResponse();
        when(mediaService.getMediaById(1L)).thenReturn(response);

        assertEquals(response, mediaController.getById(1L).getBody());
    }

    @Test
    void getByUploader_AsOwner_ShouldReturnList() {
        List<MediaResponse> responses = List.of(new MediaResponse());
        when(mediaService.getMediaByUploader(1L)).thenReturn(responses);

        assertEquals(responses, mediaController.getByUploader(1L, "1", "AUTHOR").getBody());
    }

    @Test
    void getByUploader_WhenForbidden_ShouldThrow() {
        CustomException exception = assertThrows(CustomException.class,
                () -> mediaController.getByUploader(1L, "2", "AUTHOR"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void getByPost_ShouldReturnList() {
        List<MediaResponse> responses = List.of(new MediaResponse());
        when(mediaService.getMediaByPost(9L)).thenReturn(responses);

        assertEquals(responses, mediaController.getByPost(9L).getBody());
    }

    @Test
    void updateAltText_AsOwner_ShouldReturnUpdatedMedia() {
        MediaResponse response = new MediaResponse();
        response.setUploaderId(1L);
        when(mediaService.getMediaById(7L)).thenReturn(response);
        when(mediaService.updateAltText(7L, "alt")).thenReturn(response);

        assertEquals(response, mediaController.updateAltText(7L, "alt", "1", "AUTHOR").getBody());
    }

    @Test
    void linkToPost_AsAdmin_ShouldReturnUpdatedMedia() {
        MediaResponse response = new MediaResponse();
        response.setUploaderId(2L);
        when(mediaService.getMediaById(7L)).thenReturn(response);
        when(mediaService.linkToPost(7L, 9L)).thenReturn(response);

        assertEquals(response, mediaController.linkToPost(7L, 9L, "1", "ADMIN").getBody());
    }

    @Test
    void unlinkFromPost_AsOwner_ShouldReturnUpdatedMedia() {
        MediaResponse response = new MediaResponse();
        response.setUploaderId(1L);
        when(mediaService.getMediaById(7L)).thenReturn(response);
        when(mediaService.unlinkFromPost(7L)).thenReturn(response);

        assertEquals(response, mediaController.unlinkFromPost(7L, "1", "AUTHOR").getBody());
    }

    @Test
    void delete_AsOwner_ShouldDelegate() {
        MediaResponse response = new MediaResponse();
        response.setUploaderId(1L);
        when(mediaService.getMediaById(7L)).thenReturn(response);

        mediaController.delete(7L, "1", "AUTHOR");

        verify(mediaService).deleteMedia(7L);
    }

    @Test
    void getAll_AsAdmin_ShouldReturnAllMedia() {
        List<MediaResponse> responses = List.of(new MediaResponse());
        when(mediaService.getAllMedia()).thenReturn(responses);

        assertEquals(responses, mediaController.getAll("ADMIN").getBody());
    }

    @Test
    void getAll_WhenNotAdmin_ShouldThrow() {
        CustomException exception = assertThrows(CustomException.class, () -> mediaController.getAll("AUTHOR"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void serveFile_WhenFileExists_ShouldReturnResource() throws Exception {
        FileController fileController = new FileController();
        Path file = tempDir.resolve("image.jpg");
        Files.writeString(file, "content");
        ReflectionTestUtils.setField(fileController, "localPath", tempDir.toString());

        assertEquals(HttpStatus.OK, fileController.serveFile("image.jpg").getStatusCode());
    }

    @Test
    void serveFile_WhenFileMissing_ShouldReturnNotFound() {
        FileController fileController = new FileController();
        ReflectionTestUtils.setField(fileController, "localPath", tempDir.toString());

        assertEquals(HttpStatus.NOT_FOUND, fileController.serveFile("missing.jpg").getStatusCode());
    }
}
