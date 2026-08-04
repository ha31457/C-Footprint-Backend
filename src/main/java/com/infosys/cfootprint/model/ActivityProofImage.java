package com.infosys.cfootprint.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "activity_proof_images")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityProofImage {

    @Id
    private String id;
    
    private String filename;
    private String contentType;
    private byte[] data;
    private LocalDateTime uploadedAt;
}
