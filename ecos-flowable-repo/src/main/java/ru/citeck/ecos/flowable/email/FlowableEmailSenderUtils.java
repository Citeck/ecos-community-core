package ru.citeck.ecos.flowable.email;

public class FlowableEmailSenderUtils {

    public static String[] splitAndTrim(String str) {
        if (str != null) {
            String[] splittedStrings = str.split(",");
            for (int i = 0; i < splittedStrings.length; i++) {
                splittedStrings[i] = splittedStrings[i].trim();
            }
            return splittedStrings;
        }
        return null;
    }
}
