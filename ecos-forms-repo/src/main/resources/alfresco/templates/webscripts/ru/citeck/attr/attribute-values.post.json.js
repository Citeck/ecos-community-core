(function() {

    var requests = json.get('requests'),
        nodes = {},
        values = [];

    for(var i = 0, ii = requests.length(); i < ii; i++) {
        var request = requests.get(i),
            nodeRef = request.get('nodeRef'),
            node = (nodes[nodeRef] = nodes[nodeRef] || attributes.getNodeRef(nodeRef)),
            attribute = request.get('attribute'),
            value = node ? attributes.get(node, attribute) : null;

        if (isEmptyValue(value)) {
            values.push({
                nodeRef: nodeRef,
                attribute: attribute,
                persisted: false
            });
        } else {
            values.push({
                nodeRef: nodeRef,
                attribute: attribute,
                persisted: true,
                value: value
            });
        }
    }

    model.values = values;

})();

function isEmptyValue(value) {

    if (value == null) {
        return true;
    } else if (value instanceof Packages.java.util.Collection) {
        return value.size() === 0;
    }

    return false;
}
