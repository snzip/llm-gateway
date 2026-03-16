package com.qizlan.llm.gateway.gateway.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.List;

public final class ImageDtos {

    private ImageDtos() {
    }

    public record ImageGenerationRequest(
            @NotBlank String prompt,
            String model,
            @Min(1) @Max(10) Integer n,
            String size,
            String quality,
            String response_format,
            String style,
            String aspect_ratio
    ) {
    }

    public record ImageEditRef(@NotBlank String image_url) {
    }

    public record ImageEditRequest(
            @NotEmpty List<ImageEditRef> images,
            @NotBlank String prompt,
            String background,
            String input_fidelity,
            String model,
            @Min(1) @Max(10) Integer n,
            Integer output_compression,
            String output_format,
            String quality,
            String size
    ) {
    }

    public record ImageResponse(long created, List<ImageData> data) {
        public static ImageResponse single(String revisedPrompt) {
            return new ImageResponse(
                    Instant.now().getEpochSecond(),
                    List.of(new ImageData(java.util.Base64.getEncoder().encodeToString("fake-image".getBytes()), revisedPrompt))
            );
        }
    }

    public record ImageData(String b64_json, String revised_prompt) {
    }
}
