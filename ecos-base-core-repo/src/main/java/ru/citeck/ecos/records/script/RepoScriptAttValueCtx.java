package ru.citeck.ecos.records.script;

import ru.citeck.ecos.records2.RecordRef;

public interface RepoScriptAttValueCtx {

    String getId();

    RecordRef getRef();

    String getLocalId();

    Object load(Object attributes);

    void att(String att, Object value);

    RepoScriptAttValueCtx save();

    void reset();
}
