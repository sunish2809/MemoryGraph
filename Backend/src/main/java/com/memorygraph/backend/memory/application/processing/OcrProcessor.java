package com.memorygraph.backend.memory.application.processing;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.memorygraph.backend.memory.domain.MediaAsset;
import com.memorygraph.backend.memory.domain.Memory;
import com.memorygraph.backend.memory.domain.ProcessingJobType;
import com.memorygraph.backend.storage.StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

/**
 * Appends OCR text to a photo's searchable content when Tesseract is available on the host.
 * <p>
 * Missing native libraries or an undecodable image are soft skips — the memory stays COMPLETED and
 * the caption / embedding steps still run.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcrProcessor implements MemoryProcessor {

    private final StorageService storageService;

    @Override
    public ProcessingJobType jobType() {
        return ProcessingJobType.OCR;
    }

    @Override
    public void process(Memory memory) {
        List<String> ocrParts = new ArrayList<>();
        for (MediaAsset asset : memory.getAssets()) {
            extractText(asset).ifPresent(ocrParts::add);
        }
        if (ocrParts.isEmpty()) {
            return;
        }
        String existing = memory.getContent() == null ? "" : memory.getContent().strip();
        String ocrBlock = String.join("\n", ocrParts).strip();
        if (!StringUtils.hasText(ocrBlock)) {
            return;
        }
        memory.updateSearchableContent(StringUtils.hasText(existing) ? existing + "\n" + ocrBlock : ocrBlock);
    }

    private java.util.Optional<String> extractText(MediaAsset asset) {
        try (InputStream stream = storageService.retrieve(asset.key()).getInputStream()) {
            BufferedImage image = ImageIO.read(stream);
            if (image == null) {
                log.debug("OCR skipped for {}: ImageIO could not decode", asset.getFileName());
                return java.util.Optional.empty();
            }
            Tesseract tesseract = new Tesseract();
            tesseract.setLanguage("eng");
            String text = tesseract.doOCR(image);
            if (!StringUtils.hasText(text)) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(text.strip());
        } catch (UnsatisfiedLinkError | NoClassDefFoundError ex) {
            log.info("OCR skipped: Tesseract native library is not available ({})", ex.toString());
            return java.util.Optional.empty();
        } catch (TesseractException ex) {
            log.warn("OCR failed for asset {}: {}", asset.getId(), ex.getMessage());
            return java.util.Optional.empty();
        } catch (Exception ex) {
            log.warn("OCR skipped for asset {}", asset.getId(), ex);
            return java.util.Optional.empty();
        }
    }
}
