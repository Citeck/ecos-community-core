package ru.citeck.ecos.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.alfresco.repo.jscript.*;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.namespace.QName;
import org.json.JSONArray;
import org.json.JSONObject;
import org.mozilla.javascript.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records2.RecordRef;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.*;

@Component
public class JsUtils {

    private static final String TYPE_DATE = "Date";

    private ObjectMapper mapper = new ObjectMapper();
    private ServiceRegistry serviceRegistry;

    private WrapFactory wrapFactory = new RhinoWrapFactory();
    private Scriptable rootScope;

    @PostConstruct
    public void init() {

        Context cx = Context.enter();
        try {
            cx.setWrapFactory(wrapFactory);
            rootScope = new ImporterTopLevel(cx, true);
        } finally {
            Context.exit();
        }
    }

    public <T> List<T> getList(Object list, java.util.function.Function<Object, T> mapFunc) {

        List<T> result = new ArrayList<>();

        Object javaList = toJava(list);
        if (javaList instanceof Iterable) {
            for (Object value : (Iterable) javaList) {
                result.add(mapFunc.apply(value));
            }
        }
        return result;
    }

    public RecordRef getRecordRef(Object object) {
        if (object == null) {
            return null;
        }
        if (object instanceof RecordRef) {
            return (RecordRef) object;
        }
        if (object instanceof NodeRef) {
            return RecordRef.valueOf(((NodeRef) object).toString());
        }
        if (object instanceof String) {
            return RecordRef.valueOf((String) object);
        }
        if (object instanceof ScriptNode) {
            return RecordRef.valueOf(((ScriptNode) object).getNodeRef().toString());
        }
        throw new IllegalArgumentException("Can not convert from " + object.getClass() + " to RecordRef");
    }

    public Object toJava(Object value) {

        if (value == null) {

            return null;

        } else if (value instanceof ScriptNode) {

            // convert back to NodeRef
            value = ((ScriptNode) value).getNodeRef();

        } else if (value instanceof ChildAssociation) {

            value = ((ChildAssociation) value).getChildAssociationRef();

        } else if (value instanceof Association) {

            value = ((Association) value).getAssociationRef();

        } else if (value instanceof Wrapper) {

            // unwrap a Java object from a JavaScript wrapper
            // recursively call this method to convert the unwrapped value
            value = toJava(((Wrapper) value).unwrap());

        } else if (value instanceof Scriptable) {

            value = scriptableToJava(value);

        } else if (value.getClass().isArray()) {

            // convert back a list of Java values
            int length = Array.getLength(value);
            ArrayList<Object> list = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                list.add(toJava(Array.get(value, i)));
            }
            value = list;

        } else if (value instanceof CharSequence) {

            // Rhino has some interesting internal classes such as ConsString which cannot be cast to String
            // but fortunately are instanceof CharSequence so we can toString() them.
            value = value.toString();

        } else if (value instanceof JSONArray) {

            ArrayList<Object> valuesList = new ArrayList<>();
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                valuesList.add(toJava(array.opt(i)));
            }
            value = valuesList;

        } else if (value instanceof JSONObject) {

            Map<String, Object> valuesMap = new HashMap<>();
            JSONObject jsonObj = (JSONObject) value;

            Iterator keys = jsonObj.keys();
            while (keys.hasNext()) {
                String key = String.valueOf(keys.next());
                valuesMap.put(key, toJava(jsonObj.opt(key)));
            }

            value = valuesMap;
        }

        return value;
    }

    private Object scriptableToJava(Object value) {
        // a scriptable object will probably indicate a multi-value property
        // set using a JavaScript Array object
        Scriptable values = (Scriptable) value;

        if (value instanceof IdScriptableObject) {
            // TODO: add code here to use the dictionary and convert to correct value type
            if (TYPE_DATE.equals(((IdScriptableObject) value).getClassName())) {
                value = Context.jsToJava(value, Date.class);
            } else if (value instanceof NativeArray) {
                value = nativeArrayToJava(value);
            } else {
                value = idScriptableToJava(values);
            }
        } else {
            // convert Scriptable object of values to a Map of objects
            Object[] propIds = values.getIds();
            Map<String, Object> propValues = new HashMap<>(propIds.length);

            // work on each key in turn
            // we are only interested in keys that indicate a list of values
            // get the value out for the specified key
            // recursively call this method to convert the value
            Arrays.stream(propIds)
                    .filter(propId -> propId instanceof String)
                    .forEach(propId -> {

                        Object val = values.get((String) propId, values);
                        propValues.put((String) propId, toJava(val));
                    });
            value = propValues;
        }

        return value;
    }

    private Object nativeArrayToJava(Object value) {
        Scriptable values = (Scriptable) value;

        // convert JavaScript array of values to a List of objects
        Object[] propIds = values.getIds();

        if (isArray(propIds)) {

            List<Object> propValues = new ArrayList<>(propIds.length);
            for (Object propId : propIds) {
                // work on each key in turn
                // we are only interested in keys that indicate a list of values
                if (propId instanceof Integer) {
                    // get the value out for the specified key
                    Object val = values.get((Integer) propId, values);
                    // recursively call this method to convert the value
                    propValues.add(toJava(val));
                }
            }

            value = propValues;

        } else {

            Map<Object, Object> propValues = new HashMap<>(propIds.length);
            for (Object propId : propIds) {
                // Get the value and add to the map
                Object val = values.get(propId.toString(), values);
                propValues.put(toJava(propId), toJava(val));
            }

            value = propValues;
        }

        return value;
    }

    private Object idScriptableToJava(Scriptable values) {
        // convert Scriptable object of values to a Map of objects
        Object[] propIds = values.getIds();
        Map<String, Object> propValues = new HashMap<>(propIds.length);

        for (Object propId : propIds) {
            // work on each key in turn
            // we are only interested in keys that indicate a list of values
            if (propId instanceof String) {
                // get the value out for the specified key
                Object val = values.get((String) propId, values);
                // recursively call this method to convert the value
                propValues.put((String) propId, toJava(val));
            }
        }

        return propValues;
    }

    /**
     * Look at the id's of a native array and try to determine whether it's actually an Array or a Hashmap
     *
     * @param ids id's of the native array
     * @return boolean  true if it's an array, false otherwise (ie it's a map)
     */
    private boolean isArray(Object[] ids) {
        boolean result = true;
        for (Object id : ids) {
            if (!(id instanceof Integer)) {
                result = false;
                break;
            }
        }
        return result;
    }

    /**
     * Perform conversions from Java objects to JavaScript scriptable instances
     */
    public Object toScript(Object value) {

        Scriptable scope = rootScope;

        if (value == null) {

            return null;

        } else if (value instanceof NodeRef) {

            // NodeRef object properties are converted to new Node objects
            // so they can be used as objects within a template
            value = new ScriptNode(((NodeRef) value), serviceRegistry, scope);

        } else if (value instanceof QName || value instanceof StoreRef) {

            value = value.toString();

        } else if (value instanceof ChildAssociationRef) {

            value = new ChildAssociation(serviceRegistry, (ChildAssociationRef) value, scope);

        } else if (value instanceof AssociationRef) {

            value = new Association(serviceRegistry, (AssociationRef) value, scope);

        } else if (value instanceof Date) {

            // convert Date to JavaScript native Date object
            // call the "Date" constructor on the root scope object - passing in the millisecond
            // value from the Java date - this will construct a JavaScript Date with the same value
            Date date = (Date) value;

            try {
                Context.enter();
                value = ScriptRuntime.newObject(Context.getCurrentContext(),
                        scope,
                        TYPE_DATE,
                        new Object[]{date.getTime()});
            } finally {
                Context.exit();
            }

        } else if (value instanceof Collection) {

            // recursively convert each value in the collection
            Collection<Serializable> collection = (Collection<Serializable>) value;
            Object[] array = new Object[collection.size()];
            int index = 0;
            for (Serializable obj : collection) {
                array[index++] = toScript(obj);
            }
            try {

                Context.enter();
                // Convert array to a native JavaScript Array
                // Note - a scope is usually required for this to work
                value = Context.getCurrentContext().newArray(scope, array);

            } finally {

                Context.exit();
            }
        }
        // simple numbers and strings are wrapped automatically by Rhino

        return value;
    }

    public <T> T convert(Object obj, Class<T> toClass) {

        if (obj instanceof String) {

            if (toClass == String.class) {
                // This cast is correct, because we know this is a String
                @SuppressWarnings("unchecked") T result = (T) obj;
                return result;
            }

            String objStr = (String) obj;
            int i = 0;
            while (i < objStr.length() && Character.isWhitespace(objStr.charAt(i))) {
                i++;
            }

            char firstChar = i < objStr.length() ? objStr.charAt(i) : ' ';

            try {
                if (firstChar == '{' || firstChar == '[') {
                    return mapper.readValue(objStr, toClass);
                }
            } catch (IOException e) {
                String msg = "Could not read value from object. Object=%s, class=%s";
                throw new RuntimeException(String.format(msg, obj, toClass), e);
            }
        }

        return mapper.convertValue(obj, toClass);
    }

    public <T> T toJava(Object jsObject, Class<T> javaClass) {
        return convert(toJava(jsObject), javaClass);
    }

    @Autowired
    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
    }

    private static class RhinoWrapFactory extends WrapFactory {

        public Scriptable wrapAsJavaObject(Context cx, Scriptable scope, Object javaObject, Class staticType) {

            if (javaObject instanceof Map && !(javaObject instanceof ScriptableHashMap)) {
                return new NativeMap(scope, (Map) javaObject);
            }
            return super.wrapAsJavaObject(cx, scope, javaObject, staticType);
        }
    }
}
