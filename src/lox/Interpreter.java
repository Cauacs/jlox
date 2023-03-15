package lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.events.EndElement;

import lox.Expr.Variable;
import lox.Stmt.Var;

/**
 * Intepreter
 */
public class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
    // store here so that it stays in memory as long as the interpreter its still
    // running
    final Enviroment globals = new Enviroment();
    private Enviroment enviroment = globals;
    private final Map<Expr, Integer> locals = new HashMap<>();

    Interpreter() {
        globals.define("clock", new LoxCallable() {
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return (double) System.currentTimeMillis();
            }

            @Override
            public String toString() {
                return "<native fn>";
            }
        });
    }

    public void interpret(List<Stmt> statements) {
        try {
            for (Stmt statement : statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
        }
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {

        // evalute left expr first
        Object left = evaluate(expr.left);

        // check if operator is OR
        if (expr.operator.type == TokenType.OR) {
            // if left is true return left(true)
            if (isTruthy(left))
                return left;
        } else {
            // if left is false return left(false)
            if (!isTruthy(left))
                return left;
        }

        // only then evaluate the right expr and return it
        return evaluate(expr.right);
    }

    @Override
    public Object visitSetExpr(Expr.Set expr) {
        // evaluate the obj whose property is being set
        Object object = evaluate(expr.object);

        // check to see if its a LoxInstace
        if (!(object instanceof LoxInstance)) {
            throw new RuntimeError(expr.name, "Only instances have fields");
        }

        // evaluate the value being set
        Object value = evaluate(expr.value);
        // cast down to LoxInstace and set it
        ((LoxInstance) object).set(expr.name, value);

        return value;
    }

    /*
     * This is almost exactly like the code for looking up a method of a get
     * expression, except that we call findMethod() on the superclass instead of on
     * the class of the current object.
     */

    public Object visitSuperExpr(Expr.Super expr) {
        // get the distance to the variable
        int distance = locals.get(expr);
        LoxClass superclass = (LoxClass) enviroment.getAt(distance, "super");
        LoxInstance object = (LoxInstance) enviroment.getAt(distance - 1, "this");

        LoxFunction method = superclass.findMethod(expr.method.lexeme);

        if (method == null) {
            throw new RuntimeError(expr.method, "Undefine property '" + expr.method.lexeme + "'.");
        }
        return method.bind(object);

    }

    @Override
    public Object visitThisExpr(Expr.This expr) {
        return lookUpVariable(expr.keyword, expr);
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {

        // evaluate the expression first
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case BANG:
                return !isTruthy(right);
            case MINUS:
                checkNumberOperand(expr.operator, right);
                return -(double) right;
        }
        // unreachble
        return null;
    }

    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double)
            return;
        throw new RuntimeError(operator, "Operand must be a number");
    }

    private void checkNumberOperands(Token operator,
            Object left, Object right) {
        if (left instanceof Double && right instanceof Double)
            return;

        throw new RuntimeError(operator, "Operands must be numbers.");
    }

    private boolean isTruthy(Object object) {
        if (object == null)
            return false;
        if (object instanceof Boolean)
            return (boolean) object;
        return true;
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null)
            return true;
        if (a == null)
            return false;

        return a.equals(b);
    }

    private String stringify(Object object) {
        if (object == null)
            return "nil";

        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }

        return object.toString();
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    void resolve(Expr expr, int depth) {
        locals.put(expr, depth);
    }

    void executeBlock(List<Stmt> statements, Enviroment enviroment) {
        // store the previous enviroment
        Enviroment previous = this.enviroment;
        try {
            // update the enviroment to the new scope
            this.enviroment = enviroment;
            // execute the statements
            for (Stmt statement : statements)
                execute(statement);
        } finally {
            // restores the enviroment to the previous
            this.enviroment = previous;
        }
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Enviroment(enviroment));
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        Object superclass = null;
        if (stmt.superclass != null) {
            superclass = evaluate(stmt.superclass);
            if (!(superclass instanceof LoxClass)) {
                throw new RuntimeError(stmt.superclass.name, "Superclass must be a class");
            }
        }

        // declare the class name in the current env
        enviroment.define(stmt.name.lexeme, null);
        // turn the class syntax node into a LoxClass
        // LoxClass is the runtime representation of a class

        if (stmt.superclass != null) {
            enviroment = new Enviroment(enviroment);
            enviroment.define("super", superclass);
        }

        Map<String, LoxFunction> methods = new HashMap<>();
        // each method turns into a LoxFunction object
        // wrap them into a map that its stored in LoxClass
        for (Stmt.Function method : stmt.methods) {
            LoxFunction function = new LoxFunction(method, enviroment, method.name.lexeme.equals("init"));
            methods.put(method.name.lexeme, function);
        }

        Map<String, Object> staticMethods = new HashMap<>();
        for (Stmt.Function staticMethod : stmt.staticMethods) {
            Object function = new LoxFunction(staticMethod, enviroment, false);
            staticMethods.put(staticMethod.name.lexeme, function);
        }

        LoxClass klass;

        if (staticMethods.size() == 0) {
            klass = new LoxClass(stmt.name.lexeme, (LoxClass) superclass, methods);
        } else {
            klass = new LoxClass(stmt.name.lexeme, (LoxClass) superclass, methods, staticMethods);
        }

        if (superclass != null) {
            enviroment = enviroment.enclosing;
        }

        // store the class object in the variable we previously declared.
        enviroment.assign(stmt.name, klass);
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        Object value = evaluate(stmt.expression);
        if (Lox.isPromptMode)
            System.out.println(stringify(value));
        return null;
    }

    // take a function syntax node
    // a compile-time representation of the function
    // and convert it to its runtime representation
    public Void visitFunctionStmt(Stmt.Function stmt) {
        // passes the enviroment of when the function is DECLARED not called.
        LoxFunction function = new LoxFunction(stmt, enviroment, false);
        // define the function name on the enviroment
        enviroment.define(stmt.name.lexeme, function);
        return null;
    }

    // @Override
    // public Void visitLambdaStmt(Lambda stmt) {
    // // TODO Auto-generated method stub
    // throw new UnsupportedOperationException("Unimplemented method
    // 'visitLambdaStmt'");
    // }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }

        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        Object value = null;
        if (stmt.value != null)
            value = evaluate(stmt.value);

        throw new Return(value);
    }

    @Override
    public Void visitVarStmt(Var stmt) {
        Object value = null;
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }
        enviroment.define(stmt.name.lexeme, value);
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        while (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.body);
        }

        return null;
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        // evaluate the value
        Object value = evaluate(expr.value);

        // check the distance
        Integer distance = locals.get(expr);
        // check to see if its global(the resolver returns null for globals)
        if (distance != null) {
            enviroment.assignAt(distance, expr.name, value);
        } else {
            globals.assign(expr.name, value);
        }

        return value;
    }

    @Override
    public Object visitVariableExpr(Variable expr) {
        return lookUpVariable(expr.name, expr);
    }

    private Object lookUpVariable(Token name, Expr expr) {
        // look up resolved distance in the map
        Integer distance = locals.get(expr);
        // global variables are null, we dont resolve them
        if (distance != null) {
            return enviroment.getAt(distance, name.lexeme);
        } else {
            return globals.get(name);
        }
    }

    public Object visitBinaryExpr(Expr.Binary expr) {

        // evaluate left to right
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case BANG_EQUAL:
                return !isEqual(left, right);
            case EQUAL_EQUAL:
                return isEqual(left, right);
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double) left > (double) right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double) left >= (double) right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double) left < (double) right;
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double) left <= (double) right;
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double) left - (double) right;
            case PLUS:
                if (left instanceof Double && right instanceof Double) {
                    return (double) left + (double) right;
                }
                if (left instanceof String && right instanceof String) {
                    return (String) left + (String) right;
                }
                if (left instanceof String && right instanceof Double) {
                    return (String) left + (String) stringify(right);
                }
                if (left instanceof Double && right instanceof String) {
                    return (String) stringify(left) + (String) right;
                }
                throw new RuntimeError(expr.operator, "Operands must be two numbers or two strings");
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                return (double) left / (double) right;
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double) left * (double) right;

        }

        return null;
    }

    @Override
    public Object visitCallExpr(Expr.Call expr) {
        // evaluate the expr for the callee
        Object callee = evaluate(expr.callee);

        // evaluate each of the arguments expressions.
        List<Object> arguments = new ArrayList<>();
        for (Expr argument : expr.arguments) {
            arguments.add(evaluate(argument));
        }

        if (!(callee instanceof LoxCallable)) {
            throw new RuntimeError(expr.paren, "Can only call functions and classes");
        }

        LoxCallable function = (LoxCallable) callee;
        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren, "Expected " +
                    function.arity() + " arguments but got " +
                    arguments.size() + ".");
        }
        return function.call(this, arguments);
    }

    @Override
    public Object visitGetExpr(Expr.Get expr) {
        // evaluate the expression whose property is being accessed
        Object object = evaluate(expr.object);

        if (object instanceof LoxInstance) {
            return ((LoxInstance) object).get(expr.name);
        }

        throw new RuntimeError(expr.name, "Only instances have properties");
    }

}