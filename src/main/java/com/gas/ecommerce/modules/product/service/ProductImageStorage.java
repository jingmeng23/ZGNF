package com.gas.ecommerce.modules.product.service;

import com.gas.ecommerce.common.exception.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProductImageStorage {
    public static final String URL_PREFIX = "/api/v1/media/products/";
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private final Path directory;

    public ProductImageStorage(@Value("${app.product-images.directory:./uploads/products}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    public record UploadedImage(String url) { }

    public UploadedImage upload(MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new BusinessException("请选择要上传的商品图片");
        if (file.getSize() > MAX_BYTES) throw new BusinessException("单张图片不能超过 10MB");
        byte[] bytes;
        try (var input = file.getInputStream()) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES) throw new BusinessException("单张图片不能超过 10MB");
        String extension = validate(bytes);
        // Use a server-generated name and the decoded format, never the supplied filename or MIME type.
        String name = UUID.randomUUID() + "." + extension;
        Files.createDirectories(directory);
        Path target = directory.resolve(name);
        try {
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException ex) {
            Files.deleteIfExists(target);
            throw ex;
        }
        return new UploadedImage(URL_PREFIX + name);
    }

    private String validate(byte[] bytes) {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BusinessException("请选择有效的 JPG、PNG 或 GIF 图片");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!java.util.Set.of("jpeg", "png", "gif").contains(format)) {
                    throw new BusinessException("仅支持 JPG、PNG 或 GIF 图片");
                }
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > 40_000_000) {
                    throw new BusinessException("图片尺寸过大，请缩小后上传（最多 4000 万像素）");
                }
                reader.read(0);
                return format.equals("jpeg") ? "jpg" : format;
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException ex) {
            throw new BusinessException("图片文件已损坏，请重新选择");
        }
    }
}
