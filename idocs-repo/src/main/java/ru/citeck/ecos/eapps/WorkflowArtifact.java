package ru.citeck.ecos.eapps;

import lombok.Data;

@Data
public class WorkflowArtifact {
    private String id;
    private byte[] xmlData;
}
