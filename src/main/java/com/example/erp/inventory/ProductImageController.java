package com.example.erp.inventory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

@RestController
class ProductImageController {
    private final Path uploadDirectory;

    ProductImageController(@Value("${app.upload-dir:uploads/products}") String uploadDirectory) {
        this.uploadDirectory = Paths.get(uploadDirectory).toAbsolutePath().normalize();
    }

    @PostMapping(value = "/api/inventory/product-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<Map<String, String>> upload(@RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            return ResponseEntity.badRequest().build();
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest().build();
        }
        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        String filename = UUID.randomUUID() + (extension == null ? ".img" : "." + extension.toLowerCase());
        Files.createDirectories(uploadDirectory);
        Files.copy(file.getInputStream(), uploadDirectory.resolve(filename));
        return ResponseEntity.ok(Map.of("url", "/uploads/products/" + filename));
    }

    @GetMapping("/uploads/products/{filename:.+}")
    ResponseEntity<Resource> get(@PathVariable String filename) throws MalformedURLException {
        Path file = uploadDirectory.resolve(filename).normalize();
        if (!file.startsWith(uploadDirectory)) return ResponseEntity.notFound().build();
        Resource resource = new UrlResource(file.toUri());
        if (!resource.exists()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(mediaType(filename)).body(resource);
    }

    private MediaType mediaType(String filename) {
        String type = switch (StringUtils.getFilenameExtension(filename).toLowerCase()) {
            case "png" -> MediaType.IMAGE_PNG_VALUE;
            case "gif" -> MediaType.IMAGE_GIF_VALUE;
            case "webp" -> "image/webp";
            default -> MediaType.IMAGE_JPEG_VALUE;
        };
        return MediaType.parseMediaType(type);
    }
}
