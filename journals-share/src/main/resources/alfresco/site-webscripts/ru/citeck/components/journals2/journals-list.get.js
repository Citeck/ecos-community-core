<import resource="classpath:alfresco/site-webscripts/ru/citeck/components/journals2/journals.lib.js">

fillModel();

model.outputPredicates = true;
model.settingsControlMode = args.settingsControlMode;
model.loadFilterMethod = args.loadFilterMethod;

model.additionalMenuItem = getScopedConfig("Journals", "additionalMenu", "item", function(a) {return a.attributes.name;});
