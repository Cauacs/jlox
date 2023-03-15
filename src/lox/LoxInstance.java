package lox;

import java.util.HashMap;
import java.util.Map;

//the runtime representation of an instance of a lox class

public class LoxInstance {
    private LoxClass klass;
    private final Map<String, Object> fields = new HashMap<>();

    LoxInstance() {
    }

    LoxInstance(LoxClass klass) {
        this.klass = klass;
    }

    // constructor that takes methos and put in fields
    LoxInstance(LoxClass klass, Map<String, Object> staticMethods) {
        fields.putAll(staticMethods);
    }

    @Override
    public String toString() {
        return klass.name + " Instance";
    }

    Object get(Token name) {
        // check if the instance has a field with that name
        if (fields.containsKey(name.lexeme)) {
            // returns it
            return fields.get(name.lexeme);
        }

        LoxFunction method = klass.findMethod(name.lexeme);
        if (method != null)
            return method.bind(this);

        throw new RuntimeError(name, "Undefine property '" + name.lexeme + "'.");
    }

    void set(Token name, Object value) {
        fields.put(name.lexeme, value);
    }
}
