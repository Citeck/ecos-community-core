(function() {
    var available = args.available;
    if (available && available != "") {
        available = (available == "true");
    } else {
        available = !availability.getCurrentUserAvailability();
    }
    availability.setCurrentUserAvailabilityAsync(available);
    model.available = available;
})();
