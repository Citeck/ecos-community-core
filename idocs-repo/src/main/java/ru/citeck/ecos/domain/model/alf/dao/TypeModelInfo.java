package ru.citeck.ecos.domain.model.alf.dao;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.alfresco.repo.dictionary.M2Model;
import org.alfresco.service.cmr.repository.NodeRef;
import ru.citeck.ecos.records2.RecordRef;

@Data
@AllArgsConstructor
public class TypeModelInfo {

    private final NodeRef nodeRef;
    private final RecordRef typeRef;
    private final String modelPrefix;
    private final M2Model model;

    public TypeModelInfo withModel(M2Model model) {
        return new TypeModelInfo(nodeRef, typeRef, modelPrefix, model);
    }
}
