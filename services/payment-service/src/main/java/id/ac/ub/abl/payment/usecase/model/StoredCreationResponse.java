package id.ac.ub.abl.payment.usecase.model;

import java.util.Objects;

/** Snapshot teks awal; tidak direkonstruksi dari status Payment terkini. */
public record StoredCreationResponse(int statusCode, String contentType, String body) {
    public StoredCreationResponse {
        Objects.requireNonNull(body, "body");
        if (statusCode != 201 || !"application/json".equals(contentType) || body.isBlank()) {
            throw new IllegalArgumentException("Hanya respons create sukses 201 application/json yang dapat direplay");
        }
    }
}
