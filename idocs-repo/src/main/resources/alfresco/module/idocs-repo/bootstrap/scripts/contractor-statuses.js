<import resource="classpath:alfresco/module/idocs-repo/scripts/import-nodes.lib.js">

var type = '{http://www.citeck.ru/model/icase/1.0}caseStatus';
var destination = 'app:company_home/app:dictionary/cm:dataLists/cm:case-status';

var data = [
    {
        type: type,
        props: {'cm:name': 'idocs-contractor-green', 'cm:title': {'en': 'Green', 'ru': '\u0417\u0435\u043b\u0435\u043d\u044b\u0439'}}
    },
    {
        type: type,
        props: {'cm:name': 'idocs-contractor-yellow', 'cm:title': {'en': 'Yellow', 'ru': '\u0416\u0451\u043b\u0442\u044b\u0439'}}
    },
    {
        type: type,
        props: {'cm:name': 'idocs-contractor-red', 'cm:title': {'en': 'Red', 'ru': '\u041a\u0440\u0430\u0441\u043d\u044b\u0439'}}
    }
];

importNodes(destination, data);
