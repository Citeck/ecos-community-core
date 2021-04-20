package ru.citeck.ecos.workflow.records;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.codehaus.plexus.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName;
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao;
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class BpmCategoryRecordsDao implements RecordsQueryDao {

    private static final NodeRef ROOT_NODE = new NodeRef("workspace://SpacesStore/ecos-bpm-category-root");

    private final NodeService nodeService;

    @Nullable
    @Override
    public Object queryRecords(@NotNull RecordsQuery recordsQuery) {

        if (recordsQuery.getLanguage().equals("all")) {
            List<CategoryDto> categories = new ArrayList<>();
            fillSubCategories(ROOT_NODE, categories);
            return categories;
        }

        return null;
    }

    private void fillSubCategories(NodeRef root, List<CategoryDto> result) {
        List<ChildAssociationRef> childAssocs = nodeService.getChildAssocs(
            root,
            ContentModel.ASSOC_SUBCATEGORIES,
            RegexQNamePattern.MATCH_ALL
        );
        if (childAssocs.isEmpty()) {
            return;
        }
        childAssocs.forEach(assoc -> {
            NodeRef childRef = assoc.getChildRef();
            String title = (String) nodeService.getProperty(childRef, ContentModel.PROP_TITLE);
            if (StringUtils.isBlank(title)) {
                title = childRef.toString();
            }
            result.add(new CategoryDto(
                RecordRef.create("alfresco", "", childRef.toString()),
                ROOT_NODE.equals(root) ? null : "alfresco/@" + root.toString(),
                title
            ));
            fillSubCategories(childRef, result);
        });
    }

    @NotNull
    @Override
    public String getId() {
        return "bpm-category";
    }

    @Data
    public static class CategoryDto {
        private final RecordRef id;
        private final String parent;
        private final String label;

        @AttName("?disp")
        public String getDisplayName() {
            return label;
        }
    }
}
