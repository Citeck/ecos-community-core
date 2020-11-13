var statuses = [
    {name: 'idocs-contractor-green',        title: {'en': 'Green',      'ru': '\u0417\u0435\u043b\u0435\u043d\u044b\u0439'}},
    {name: 'idocs-contractor-yellow',       title: {'en': 'Yellow',     'ru': '\u0416\u0451\u043b\u0442\u044b\u0439'}},
    {name: 'idocs-contractor-red',          title: {'en': 'Red',        'ru': '\u041a\u0440\u0430\u0441\u043d\u044b\u0439'}}
];

function main(statuses) {

    var statusNode,
        repositoryHelper = services.get('repositoryHelper'),
        companyhome = search.findNode(repositoryHelper.getCompanyHome()),
        root = (companyhome.childrenByXPath("app:dictionary/cm:dataLists/cm:case-status") || [])[0];

    if (!root) {
        logger.warn("[case-statuses.js] Case statuses root not found!");
        return;
    }

    for (var i in statuses) {
        var status = statuses[i];
        statusNode = root.childByNamePath(status.name);
        if (statusNode == null) {
            statusNode = root.createNode(status.name, '{http://www.citeck.ru/model/icase/1.0}caseStatus');
        }
        utils.setLocale("en_US");
        statusNode.properties['cm:title'] = status.title.en;
        statusNode.save();
        utils.setLocale("ru_RU");
        statusNode.properties['cm:title'] = status.title.ru;
        statusNode.save();
    }
}

main(statuses);
