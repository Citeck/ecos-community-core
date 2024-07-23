package ru.citeck.ecos.records.script;

import ru.citeck.ecos.webapp.api.entity.EntityRef;

public interface RepoScriptAttValueCtx {

    String getId();

    EntityRef getRef();

    String getLocalId();

    Object load(Object attributes);

    void att(String att, Object value);

    RepoScriptAttValueCtx save();

    void reset();
}
