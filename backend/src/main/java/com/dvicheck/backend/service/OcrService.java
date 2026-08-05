package com.dvicheck.backend.service;

import com.dvicheck.backend.exception.DvicheckException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Base64;
import java.util.List;

@Service
@Slf4j
public class OcrService {

    private final RestClient restClient;
    private final String apiKey;

    public OcrService(@Value("${dvicheck.google.vision.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.builder()
                .baseUrl("https://vision.googleapis.com/v1")
                .build();
    }

    public String extractText(byte[] imageBytes) {
        return extractText(Base64.getEncoder().encodeToString(imageBytes));
    }

    public String extractText(String imageBase64) {
        VisionRequest body = new VisionRequest(List.of(
                new AnnotateImageRequest(
                        new ImagePayload(stripDataUriPrefix(imageBase64)),
                        List.of(new Feature("TEXT_DETECTION"))
                )
        ));

        VisionResponse response;
        try {
            response = restClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/images:annotate").queryParam("key", apiKey).build())
                    .body(body)
                    .retrieve()
                    .body(VisionResponse.class);
        } catch (RestClientException e) {
            log.error("Vision API call failed", e);
            throw DvicheckException.serviceUnavailable("Receipt scanning is temporarily unavailable");
        }

        if (response == null || response.responses() == null || response.responses().isEmpty()) {
            throw DvicheckException.serviceUnavailable("Receipt scanning is temporarily unavailable");
        }

        AnnotateImageResponse result = response.responses().get(0);
        if (result.error() != null) {
            throw DvicheckException.badRequest("Could not read text from this image");
        }

        String text = result.fullTextAnnotation() != null ? result.fullTextAnnotation().text() : null;
        if (text == null || text.isBlank()) {
            throw DvicheckException.badRequest("No text found — try a clearer photo of the receipt");
        }
        return text;
    }

    private String stripDataUriPrefix(String base64) {
        int comma = base64.indexOf(',');
        return base64.startsWith("data:") && comma != -1 ? base64.substring(comma + 1) : base64;
    }

    private record VisionRequest(List<AnnotateImageRequest> requests) {}
    private record AnnotateImageRequest(ImagePayload image, List<Feature> features) {}
    private record ImagePayload(String content) {}
    private record Feature(String type) {}
    private record VisionResponse(List<AnnotateImageResponse> responses) {}
    private record AnnotateImageResponse(FullTextAnnotation fullTextAnnotation, VisionError error) {}
    private record FullTextAnnotation(String text) {}
    private record VisionError(int code, String message) {}
}
