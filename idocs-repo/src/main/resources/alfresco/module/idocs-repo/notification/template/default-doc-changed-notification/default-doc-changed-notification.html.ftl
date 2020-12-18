<html>
<head>
    <style type="text/css"></style>
</head>
<body bgcolor="white">
    <#if documentName??>
        <div style="font-size: 14px; margin: 0px 0px 0px 0px; padding-top: 0px; border-top: 0px solid #aaaaaa;">
            <p>В документе <span style="text-decoration:underline">${documentName}</span> произошли изменения. </p>
        </div>
        <p>Изменения: </p>
        <#if properties?? && properties?size != 0>
            <#list properties as prop>
                <div style="font-size: 14px; margin: 0px 0px 0px 20px; padding-top: 0px; border-top: 0px solid #aaaaaa;">
                    <#if prop.title?? >
                        свойство: <i>${prop.title}</i>,
                        <#if prop.before?? >
                            значение до:
                            <#if prop.before?is_boolean>
                                <i>${prop.before?date?string("dd.MM.yyyy")}</i>.
                            <#else>
                                <#attempt>
                                    <i>${prop.before?datetime.iso?string("dd.MM.yyyy")}</i>.
                                <#recover>
                                    <i>${prop.before}</i>.
                                </#attempt>
                            </#if>
                        </#if>
                        <#if prop.after?? >
                            значение после:
                            <#if prop.after?is_boolean>
                                <i>${prop.after?date?string("dd.MM.yyyy")}</i>.
                            <#else>
                                <#attempt>
                                    <i>${prop.after?datetime.iso?string("dd.MM.yyyy")}</i>.
                                <#recover>
                                    <i>${prop.after}</i>.
                                </#attempt>
                            </#if>
                        </#if>
                    <#elseif prop.event?? >
                        <#if prop.event == "added">
                            <i>Добавлена ассоциация с </i>
                        <#elseif prop.event == "deleted">
                            <i>Удалена ассоциация с </i>
                        </#if>
                        <#if prop.target?? >
                            <#if (prop.target.type!"") == "cm:person">
                                <i>${prop.target.lastName!""} ${prop.target.firstName!""}. </i>
                            <#elseif prop.target.title?? >
                                <i>${prop.target.title}. </i>
                            <#elseif prop.target.name?? >
                                <i>${prop.target.name}. </i>
                            </#if>
                        </#if>
                        <#if prop.type?? >
                            <i>Тип ассоциации: ${prop.type}</i>
                        </#if>
                    </#if>
                </div>
            </#list>
        </#if>
        <p>
            Автор изменений 
            <#if modifier??>
                <span style="text-decoration:underline">${modifier}</span>
            </#if><#if (modified)??>, дата изменений <span style="text-decoration:underline">${modified?datetime.iso?string("dd.MM.yyyy")}</span></#if>.
            <br>
        </p>
    </#if>
</body>
</html>