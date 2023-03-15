package lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Enviroment {
    final Enviroment enclosing;

    private final Map<String, Object> values = new HashMap<>();
    private final List<String> nonAssignedVars = new ArrayList<>();

    // constructor for the global scope, which ends the chain
    Enviroment() {
        enclosing = null;
    }

    // normal constructor that references another one.
    Enviroment(Enviroment enclosing) {
        this.enclosing = enclosing;
    }

    void define(String name, Object value) {
        if (value == null) {
            defineNonAssignedVar(name);
        } else {
            values.put(name, value);
        }
    }

    Enviroment ancestor(int distance) {
        Enviroment enviroment = this;
        // walk up the enviroment chain to the passed distance
        for (int i = 0; i < distance; i++) {
            enviroment = enviroment.enclosing;
        }
        return enviroment;
    }

    Object getAt(int distance, String name) {
        // return the value at that enviroment
        return ancestor(distance).values.get(name);
        // theres no need to check because the resolver already found it
    }

    void assignAt(int distance, Token name, Object value) {
        ancestor(distance).values.put(name.lexeme, value);
    }

    void defineNonAssignedVar(String name) {
        nonAssignedVars.add(name);
    }

    void assign(Token name, Object value) {
        if (values.containsKey(name.lexeme)) {
            values.put(name.lexeme, value);
            return;
        }

        if (nonAssignedVars.contains(name.lexeme)) {
            nonAssignedVars.remove(name.lexeme);
            values.put(name.lexeme, value);
            return;
        }

        if (enclosing != null) {
            enclosing.assign(name, value);
            return;
        }

        throw new RuntimeError(name, "Undefined variable '" + name.lexeme + "'.");
    }

    Object get(Token name) {
        if (values.containsKey(name.lexeme)) {
            return values.get(name.lexeme);
        }

        // check for variable in other scopes.
        if (enclosing != null)
            return enclosing.get(name);

        if (nonAssignedVars.contains(name.lexeme))
            throw new RuntimeError(name, "Unnasigned variable '" + name.lexeme + "'.");

        throw new RuntimeError(name, "Undefine variable '" + name.lexeme + "'.");
    }
}
