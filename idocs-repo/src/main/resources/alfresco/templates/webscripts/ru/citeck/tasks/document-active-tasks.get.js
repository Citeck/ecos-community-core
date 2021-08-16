(function () {
    if(!args.nodeRef) {
        status.setStatus(status.STATUS_BAD_REQUEST, "Parameter 'nodeRef' is mandatory");
        return;
    }
    var node = search.findNode(args.nodeRef);
    var tasks = node.assocs["wfm:mirrorTask"];
    var taskNodeRef = [];
    if (tasks == undefined) {
        tasks = "";
    } else {
        for each(task in tasks) {
            if (task != null && task.length != 0 && task.hasPermission("Read")) {
                if (task.properties['bpm:completionDate'] == null) {
                    var taskTitle = task.properties['cwf:taskTitle'];
                    var taskTitleMessage = null;
                    if (taskTitle != null) {
                        taskTitleMessage = Packages.org.springframework.extensions.surf.util.I18NUtil.getMessage(taskTitle);
                        if (taskTitleMessage == null) {
                            taskTitleMessage = taskTitle;
                        }
                    }

                    taskNodeRef.push({
                        'nodeRef': task.nodeRef.toString(),
                        'name': task.name,
                        'title': taskTitleMessage,
                        'task': task
                    });
                }
            }
        }
    }
    model.data = {
        'tasks': taskNodeRef
    };
})();
