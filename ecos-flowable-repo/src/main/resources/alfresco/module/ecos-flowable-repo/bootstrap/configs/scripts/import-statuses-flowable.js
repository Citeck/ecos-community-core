<import resource="classpath:alfresco/module/idocs-repo/scripts/import-nodes.lib.js">

var type = '{http://www.citeck.ru/model/icase/1.0}caseStatus';
var destination = 'app:company_home/app:dictionary/cm:dataLists/cm:case-status';

var data = [
    {type: type, props: {
        'cm:name': 'ecos-flowable-timer-error',
        'cm:title': {'en': 'Timer start error', 'ru': '\u041e\u0448\u0438\u0431\u043a\u0430 \u043f\u0440\u0438 \u0441\u0442\u0430\u0440\u0442\u0435 \u0442\u0430\u0439\u043c\u0435\u0440\u0430'}
    }}
];

importNodes(destination, data);
