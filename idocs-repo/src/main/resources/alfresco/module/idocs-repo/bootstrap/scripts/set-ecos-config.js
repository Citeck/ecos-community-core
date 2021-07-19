
if (!args.key) {
    throw "key argument is missing"
}
ecosConfigService.setValue(args.key, args.value);
