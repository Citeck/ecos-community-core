package ru.citeck.ecos.node.etype;

import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.records2.RecordRef;

import javax.annotation.PostConstruct;
import java.util.Map;

public class EcosTypeRootRegistrar {

    private final EcosTypeRootService typeRootService;

    private Map<String, String> typeRoots;

    @Autowired
    public EcosTypeRootRegistrar(EcosTypeRootService typeRootService) {
        this.typeRootService = typeRootService;
    }

    @PostConstruct
    public void init() {
        if (typeRoots != null) {
            typeRoots.forEach((typeId, path) -> {
                RecordRef typeRef = RecordRef.create("emodel", "type", typeId);
                typeRootService.registerRoot(typeRef, path);
            });
        }
    }

    @Autowired
    public void setTypeRoots(Map<String, String> setRoots) {
        this.typeRoots = setRoots;
    }
}
