package lox;

import java.sql.ClientInfoStatus;
import java.util.List;

public class LoxFunction implements LoxCallable {
    private final Stmt.Function declaration;
    private final Enviroment closure;
    private final boolean isInitializer;

    LoxFunction(Stmt.Function declaration, Enviroment closure, boolean isInitializer) {
        this.closure = closure;
        this.isInitializer = isInitializer;
        this.declaration = declaration;
    }

    LoxFunction bind(LoxInstance instance) {
        Enviroment enviroment = new Enviroment(closure);
        enviroment.define("this", instance);
        return new LoxFunction(declaration, enviroment, isInitializer);
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        // each function gets their ownd enviroment
        // enviroments are created dynamically, each function call gets its own
        Enviroment enviroment = new Enviroment(closure);
        // looping through the parameter and argument list
        for (int i = 0; i < declaration.params.size(); i++) {
            // for each pair creates a new variable with the parameter's name and binds it
            // to the argument value
            // define takes a String name and a Object value
            enviroment.define(declaration.params.get(i).lexeme, arguments.get(i));
        }
        // then it tells the interpreter to execute the body of the function in this new
        // function-local enviroment
        try {
            interpreter.executeBlock(declaration.body, enviroment);

        } catch (Return returnValue) {
            if (isInitializer)
                return closure.getAt(0, "this");
            return returnValue.value;
        }

        if (isInitializer)
            return closure.getAt(0, "this");
        return null;
    }

    @Override
    public int arity() {
        return declaration.params.size();
    }

    @Override
    public String toString() {
        return "<fn " + declaration.name.lexeme + ">";
    }
}
