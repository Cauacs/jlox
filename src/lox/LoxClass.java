package lox;

import java.util.List;
import java.util.Map;

//runtime representation of a class;

public class LoxClass extends LoxInstance implements LoxCallable {
    final String name;
    private final Map<String, LoxFunction> methods;
    final LoxClass superClass;

    LoxClass(String name, LoxClass superClass, Map<String, LoxFunction> methods) {
        this.methods = methods;
        this.name = name;
        this.superClass = superClass;
    }

    LoxClass(String name, LoxClass superClass, Map<String, LoxFunction> methods, Map<String, Object> staticMethods) {
        super(new LoxClass("metaclass", null, null), staticMethods);
        this.methods = methods;
        this.name = name;
        this.superClass = superClass;
    }

    LoxFunction findMethod(String name) {
        if (methods.containsKey(name)) {
            return methods.get(name);
        }
        if (superClass != null) {
            return superClass.findMethod(name);
        }

        return null;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override

    // when a class is called
    public Object call(Interpreter interpreter, List<Object> arguments) {
        // create loxInstance
        LoxInstance instance = new LoxInstance(this);
        // look for init method
        LoxFunction initializer = findMethod("init");
        if (initializer != null) {
            // if found one immediately bind and invoke it;
            initializer.bind(instance).call(interpreter, arguments);
        }
        return instance;
    }

    @Override
    public int arity() {
        LoxFunction initializer = findMethod("init");
        if (initializer == null)
            return 0;
        return initializer.arity();
    }

}
