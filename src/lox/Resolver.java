package lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
    private final Interpreter interpreter;
    private final Stack<Map<String, Boolean>> scopes = new Stack<>();
    private FunctionType currentFunction = FunctionType.NONE;
    private ClassType currentClass = ClassType.NONE;

    Resolver(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    private enum FunctionType {
        NONE,
        FUNCTION,
        INITIALIZER,
        METHOD,
    }

    private enum ClassType {
        NONE,
        CLASS,
        SUBCLASS
    }

    void resolve(List<Stmt> statements) {
        for (Stmt statement : statements) {
            resolve(statement);
        }
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        beginScope();
        resolve(stmt.statements);
        endScope();
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {

        ClassType enclosingClass = currentClass;
        currentClass = ClassType.CLASS;

        declare(stmt.name);
        define(stmt.name);
        /*
         * before resolving the methods bodies we push a new scope and define this in it
         * as if it were a variable
         */

        // class can't inherit form iteself.
        if (stmt.superclass != null &&
                stmt.name.lexeme.equals(stmt.superclass.name.lexeme)) {
            Lox.error(stmt.superclass.name, "A class can't inherit from itself.");
        }

        if (stmt.superclass != null) {
            currentClass = ClassType.SUBCLASS;
            resolve(stmt.superclass);
        }

        if (stmt.superclass != null) {
            beginScope();
            scopes.peek().put("super", true);
        }

        beginScope();
        scopes.peek().put("this", true);

        // iterate through the methods in the class body and call resolvefunction
        for (Stmt.Function method : stmt.methods) {
            FunctionType declaration = FunctionType.METHOD;
            if (method.name.lexeme.equals("init")) {
                declaration = FunctionType.INITIALIZER;
            }
            resolveFunction(method, declaration);
        }

        for (Stmt.Function method : stmt.staticMethods) {
            resolveFunction(method, FunctionType.METHOD);
        }

        endScope();

        if (stmt.superclass != null)
            endScope();

        currentClass = enclosingClass;
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        declare(stmt.name);
        define(stmt.name);

        resolveFunction(stmt, FunctionType.FUNCTION);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        resolve(stmt.condition);
        resolve(stmt.thenBranch);
        if (stmt.elseBranch != null)
            resolve(stmt.elseBranch);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {

        if (currentFunction == FunctionType.NONE) {
            Lox.error(stmt.keyword, "Can't return from top-level code.");
        }

        if (stmt.value != null) {
            if (currentFunction == FunctionType.INITIALIZER) {
                Lox.error(stmt.keyword, "Can't return a value from an initializer.");
            }
            resolve(stmt.value);
        }

        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        resolve(stmt.condition);
        resolve(stmt.body);
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        // declare the variable name
        declare(stmt.name);
        if (stmt.initializer != null) {
            // resolve the initializer
            resolve(stmt.initializer);
        }
        // define the variable name
        define(stmt.name);
        return null;
    }

    @Override
    public Void visitAssignExpr(Expr.Assign expr) {
        // resolve the expr on the assigned value
        resolve(expr.value);
        // then resolve the variable beign assigned to
        resolveLocal(expr, expr.name);
        return null;
    }

    @Override
    public Void visitBinaryExpr(Expr.Binary expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitCallExpr(Expr.Call expr) {
        resolve(expr.callee);

        for (Expr argument : expr.arguments) {
            resolve(argument);
        }

        return null;
    }

    @Override
    public Void visitGetExpr(Expr.Get expr) {
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitGroupingExpr(Expr.Grouping expr) {
        resolve(expr.expression);
        return null;
    }

    @Override
    public Void visitLiteralExpr(Expr.Literal expr) {
        return null;
    }

    @Override
    public Void visitLogicalExpr(Expr.Logical expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitSetExpr(Expr.Set expr) {
        resolve(expr.value);
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitSuperExpr(Expr.Super expr) {
        // resolve as if it were a variable
        if (currentClass == ClassType.NONE) {
            Lox.error(expr.keyword, "Can't use 'super' outside of a class");
        } else if (currentClass != ClassType.SUBCLASS) {
            Lox.error(expr.keyword, "Can't use 'super' in a class with no superclass.");
        }
        resolveLocal(expr, expr.keyword);
        return null;
    }

    @Override
    public Void visitThisExpr(Expr.This expr) {

        if (currentClass == ClassType.NONE) {
            Lox.error(expr.keyword, "Can't use 'this' outside of a class");
            return null;
        }

        resolveLocal(expr, expr.keyword);
        return null;
    }

    @Override
    public Void visitUnaryExpr(Expr.Unary expr) {
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitVariableExpr(Expr.Variable expr) {

        // check to see if variable is being acessed inside its own initializer.
        if (!scopes.isEmpty() && scopes.peek().get(expr.name.lexeme) == Boolean.FALSE) {
            Lox.error(expr.name, "Can't read local variable in its own initializer.");
        }

        resolveLocal(expr, expr.name);
        return null;
    }

    private void resolve(Stmt stmt) {
        stmt.accept(this);
    }

    private void resolve(Expr expr) {
        expr.accept(this);
    }

    private void resolveFunction(Stmt.Function function, FunctionType type) {
        // store the previous value
        FunctionType enclosingFunction = currentFunction;
        currentFunction = type;

        // begins new scope
        beginScope();
        // loop through the params
        for (Token param : function.params) {
            // declare and define them
            declare(param);
            define(param);
        }
        // resolve the function body
        resolve(function.body);
        // end the scope
        endScope();
        currentFunction = enclosingFunction;
    }

    private void beginScope() {
        scopes.push(new HashMap<String, Boolean>());
    }

    private void endScope() {
        scopes.pop();
    }

    private void declare(Token name) {
        // check if there are any scopes in the stack
        if (scopes.isEmpty())
            return;

        // peek the top one and put the name and false
        Map<String, Boolean> scope = scopes.peek();

        if (scope.containsKey(name.lexeme)) {
            Lox.error(name, "Already a variable with this name in this scope.");
        }
        scope.put(name.lexeme, false);
    }

    private void define(Token name) {
        if (scopes.isEmpty())
            return;
        scopes.peek().put(name.lexeme, true);
    }

    private void resolveLocal(Expr expr, Token name) {
        // start at the innermost scope and work outwards
        // looking in each map for a matching name
        for (int i = scopes.size() - 1; i >= 0; i--) {
            /*
             * if we find the variable, we resolve it, passing in the number of scopes
             * between the current innermost scope and the scope where the variable was
             * found.
             */
            if (scopes.get(i).containsKey(name.lexeme)) {
                interpreter.resolve(expr, scopes.size() - 1 - i);
                /*
                 * if we walk through all of the block scopes and never find the variable, we
                 * leave it unresolved assuming its global.
                 */
                return;
            }
        }
    }

}
