(function() {

    var proto = search.findNode(args.nodeRef);
    if(!proto) {
        status.setCode(status.STATUS_NOT_FOUND, "Could not find case " + args.nodeRef);
        return;
    }

    /*TODO*/
    var assocs = proto.associations;
    if (proto.assocs['wfm:mirrorTask'] != null) {
        var mirrors = proto.assocs['wfm:mirrorTask'];
        for (var idx in mirrors) {
            if (mirrors[idx].properties['bpm:status'] != 'Completed') {
                throw "Currently not available the ability to save templates with running processes. Please stop the processes to save the template";
            }
        }
    } else {
        for (var i in assocs) {
            if(assocs[i][0].typeShort == 'bpm:package') {
                throw "Currently not available the ability to save templates with running processes. Please stop the processes to save the template";
            }
        }
    }

    if(!proto.hasAspect("icase:case")) {
        status.setCode(status.STATUS_BAD_REQUEST, "Node " + args.nodeRef + " is not a case");
        return;
    }

    var caseTemplateRoot = search.selectNodes("/app:company_home/app:dictionary/cm:case-templates")[0];
    if(!caseTemplateRoot) {
        status.setCode(status.STATUS_INTERNAL_ERROR, "Can not find case-templates root");
        return;
    }

    var templateProperties = {};

    var ecosType = proto.properties["tk:type"];
    if (ecosType) {
        templateProperties["icase:caseEcosType"] = ecosType;
        templateProperties["icase:caseEcosKind"] = proto.properties["tk:kind"];
    } else {
        templateProperties["icase:caseType"] = proto.type;
    }

    var template = caseTemplateRoot.createNode(null, "icase:template", templateProperties, "cm:contains");
    caseService.copyCaseToTemplate(proto, template);

    var inStream, bytes, content;

    bytes = services.get("cmmnExportService").exportCase(template.nodeRef);

    inStream = new Packages.java.io.ByteArrayInputStream(bytes);
    content = template.properties['cm:content'];
    content.setMimetype("text/xml");
    content.write(inStream);
    template.save();

    model.success = true;
    model.template = template;
})()
