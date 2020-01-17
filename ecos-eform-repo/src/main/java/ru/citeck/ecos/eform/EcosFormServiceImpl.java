package ru.citeck.ecos.eform;

import lombok.Getter;
import lombok.Setter;
import org.alfresco.util.GUID;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.eform.model.EcosFormModel;
import ru.citeck.ecos.eform.provider.FormProvider;
import ru.citeck.ecos.eform.provider.MutableFormProvider;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.graphql.meta.annotation.MetaAtt;

import java.util.*;
import java.util.stream.Collectors;

public class EcosFormServiceImpl implements EcosFormService {

    private static final String DEFAULT_KEY = "DEFAULT";
    private static final String TYPE_PREFIX = "type_";

    private Set<FormProvider> providers = new TreeSet<>(Comparator.comparing(FormProvider::getOrder));
    private MutableFormProvider newFormsStore;

    private RecordsService recordsService;

    @Override
    public EcosFormModel getDefault() {
        return getFormByKey(DEFAULT_KEY).orElseThrow(() -> new IllegalStateException("Default form is not found!"));
    }

    @Override
    public Optional<EcosFormModel> getFormByKey(String formKey) {

        EcosFormModel form = null;

        for (FormProvider provider : providers) {
            form = provider.getFormByKey(formKey);
            if (form != null) {
                break;
            }
        }

        // temp fix for old forms
        if (form == null && formKey.startsWith(TYPE_PREFIX)) {
            formKey = formKey.substring(TYPE_PREFIX.length());
            form = getFormByKey(formKey).orElse(null);
        }

        return Optional.ofNullable(form);
    }

    @Override
    public Optional<EcosFormModel> getFormByKey(List<String> formKeys) {
        if (CollectionUtils.isEmpty(formKeys)) {
            return Optional.empty();
        }

        return formKeys.stream()
                .map(this::getFormByKey)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    @Override
    public List<EcosFormModel> getFormsByKeys(List<String> formKeys) {
        if (CollectionUtils.isEmpty(formKeys)) {
            return new ArrayList<>();
        }

        return formKeys.stream()
                .distinct()
                .map(this::getFormByKey)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<EcosFormModel> getFormByRecord(RecordRef record, Boolean isViewMode) {

        if (isViewMode == null) {
            isViewMode = false;
        }

        if (isViewMode) {

            ViewFormKeys keys = recordsService.getMeta(record, ViewFormKeys.class);
            Optional<EcosFormModel> form = getFormByKey(keys.getViewKeys());
            if (!form.isPresent()) {
                form = getFormByKey(keys.getKeys());
            }
            return form;

        } else {

            FormKeys keys = recordsService.getMeta(record, FormKeys.class);
            return getFormByKey(keys.getKeys());
        }
    }

    @Override
    public Optional<EcosFormModel> getFormById(String id) {

        for (FormProvider provider : providers) {

            EcosFormModel form = provider.getFormById(id);
            if (form != null) {
                return Optional.of(form);
            }
        }
        return Optional.empty();
    }

    @Override
    public String save(EcosFormModel model) {

        if (model.getId() != null) {

            for (FormProvider provider : providers) {

                EcosFormModel form = provider.getFormById(model.getId());
                if (form != null && provider instanceof MutableFormProvider) {
                    ((MutableFormProvider) provider).save(model);
                    return model.getId();
                }
            }
        } else {

            if (model.getDefinition() == null) {
                model.setDefinition(getDefault().getDefinition().deepCopy());
            }

            model.setId(GUID.generate());
        }

        newFormsStore.create(model);
        return model.getId();
    }

    @Override
    public boolean hasForm(RecordRef record, Boolean isViewMode) {
        return getFormByRecord(record, isViewMode).isPresent();
    }

    @Override
    public void register(FormProvider formProvider) {
        providers.add(formProvider);
    }

    @Autowired
    public void setRecordsService(RecordsService recordsService) {
        this.recordsService = recordsService;
    }

    public void setNewFormsStore(MutableFormProvider newFormsStore) {
        this.newFormsStore = newFormsStore;
    }

    public static class FormKeys {

        @MetaAtt(RecordConstants.ATT_FORM_KEY + "[]?str")
        @Getter @Setter private List<String> keys;
    }

    public static class ViewFormKeys extends FormKeys {

        @MetaAtt(RecordConstants.ATT_VIEW_FORM_KEY)
        @Getter @Setter private List<String> viewKeys;
    }
}
