<#escape x as x?html>
<html>
<head>
</head>
<body bgcolor="#dddddd">
<table width="100%" cellpadding="20" cellspacing="0" border="0" bgcolor="#dddddd">
    <tr>
        <td width="100%" align="center">
            <table width="70%" cellpadding="0" cellspacing="0" bgcolor="white"
                   style="background-color: white; border: 1px solid #aaaaaa;">
                <tr>
                    <td width="100%">
                        <table width="100%" cellpadding="0" cellspacing="0" border="0">
                            <tr>
                                <td style="padding: 10px 30px 0px;">
                                    <table width="100%" cellpadding="0" cellspacing="0" border="0">
                                        <tr>
                                            <td>
                                                <table cellpadding="0" cellspacing="0" border="0">
                                                    <tr>
                                                        <td>
                                                            <img src="${image.toBase64Data("task-64.png")}"
                                                                 alt="" width="64" height="64" border="0"
                                                                 style="padding-right: 20px;"/>
                                                        </td>
                                                        <td>
                                                            <div style="font-size: 22px; padding-bottom: 4px;">
                                                                <#if isSendToAssignee??>
                                                                    Инициатор задачи отсутствует в офисе
                                                                <#else>
                                                                    <#assign isSendToAssignee = false>
                                                                </#if>
                                                                <#if isSendToInitiator??>
                                                                    Исполнитель задачи отсутствует в офисе
                                                                <#else>
                                                                    <#assign isSendToInitiator = false>
                                                                </#if>

                                                            </div>
                                                        </td>
                                                    </tr>
                                                </table>
                                                <div style="font-size: 14px; margin: 12px 0px 24px 0px; padding-top: 10px; border-top: 1px solid #aaaaaa;">

                                                    <#if isSendToAssignee == true>
                                                        <p>
                                                            <#if answerByUser??>
                                                                <#list answerByUser as key, value>
                                                                    <#if key == initiatorUserName >
                                                                        <#assign answer = value>
                                                                    </#if>
                                                                </#list>
                                                            </#if>
                                                            ${answer!"Автоответ отсутствует"}
                                                        </p>
                                                        <br/>

                                                        <p>
                                                            Инициатор задачи:&nbsp;&nbsp;
                                                            <b>
                                                                <#if initiator??>
                                                                    ${initiatorLastName!""} ${initiatorFirstName!""}.
                                                                <#else>
                                                                    (Инициатор не указан).
                                                                </#if>
                                                            </b>
                                                        </p>
                                                    </#if>

                                                    <#if isSendToInitiator == true>
                                                        <p>
                                                            Отсутствующий исполнитель(и) задачи:

                                                            <#if assignees??>
                                                                <#list assignees as person>
                                                                    <b>
                                                                        ${person.lastName!""} ${person.firstName!""}
                                                                    </b>
                                                                    <br>
                                                                    Автоответ:
                                                                    <#list answerByUser as key, value>
                                                                        <#if person.userName?? && key == person.userName >
                                                                            <#assign answer = value>
                                                                        </#if>
                                                                    </#list>
                                                                    <b>${answer!"Автоответ отсутствует"}</b>
                                                                    <br/>
                                                                </#list>
                                                            </#if>

                                                        </p>

                                                    </#if>

                                                    <p>Для редактирования задачи нажмите на ссылку:</p>
                                                    <#if documentId??>
                                                        <#assign taskUrl = web_url + "/v2/dashboard?recordRef=" + documentId />
                                                    <#else>
                                                        <#assign taskUrl = web_url + "/v2/dashboard?recordRef=wftask@" + taskId />
                                                    </#if>
                                                    <p><a href="${taskUrl}">${taskUrl}</a></p>

                                                </div>
                                            </td>
                                        </tr>
                                    </table>
                                </td>
                            </tr>
                        </table>
                    </td>
                </tr>
            </table>
        </td>
    </tr>
</table>
</body>
</html>
</#escape>