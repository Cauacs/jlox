package lox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static lox.TokenType.*;

public class Parser {

    private static class ParseError extends RuntimeException {

    }

    // flat input sequence
    private final List<Token> tokens;
    // point to the next token to be parsed.
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }

        return statements;
    }

    private Stmt declaration() {
        // trycatch to catch error and synchronize when it goes into panic mode
        try {
            // parsing starts
            // check if its variable declaration
            if (match(CLASS))
                return classDeclaration();
            if (match(FUN))
                return function("function");
            if (match(VAR))
                return varDeclaration();

            // falls to the existing statement() method
            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt classDeclaration() {
        Token name = consume(IDENTIFIER, "Expect class name");

        Expr.Variable superclass = null;
        if (match(LESS)) {
            consume(IDENTIFIER, "Expect superclass name");
            superclass = new Expr.Variable(previous());
        }

        consume(LEFT_BRACE, "Expect '{' before class body.");

        List<Stmt.Function> methods = new ArrayList<>();
        List<Stmt.Function> staticMethods = new ArrayList<>();
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            if (match(CLASS)) {
                staticMethods.add(function("this nuts"));
            } else {
                methods.add(function("method"));
            }
        }

        consume(RIGHT_BRACE, "Expect '}' after class body");

        return new Stmt.Class(name, superclass, methods, staticMethods);
    }

    // parses print and expressions statements.
    private Stmt statement() {
        if (match(FOR))
            return forStatement();
        if (match(IF))
            return ifStatement();
        if (match(PRINT))
            return printStatement();
        if (match(RETURN))
            return returnStatement();
        if (match(WHILE))
            return whileStatement();

        if (match(LEFT_BRACE))
            return new Stmt.Block(block());
        return expressionStatement();
    }

    private Stmt forStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'for'");

        Stmt initializer;
        // if the token after the '(' is a semicolon the initializer has ben omitted.
        if (match(SEMICOLON)) {
            initializer = null;
        }
        // check for var to see if its var declaration.
        else if (match(VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        Expr condition = null;
        // check for semicolon to see if clause has been omitted;
        if (!check(SEMICOLON)) {
            condition = expression();
        }
        consume(SEMICOLON, "Expect ';' after loop condition");

        Expr increment = null;
        if (!check(RIGHT_PAREN)) {
            increment = expression();
        }
        consume(RIGHT_PAREN, "Expect ')' after for clauses");

        Stmt body = statement();
        // desugaring
        // check if increment exists
        if (increment != null) {
            // replace the body with a block that contains the original body followed by an
            // expression that evaluates the increment.
            body = new Stmt.Block(Arrays.asList(
                    body,
                    new Stmt.Expression(increment)));
        }

        // take the condition and the body and build the loop using primitive while loop
        // if condition is ommited we jam true to make a infinite loop.
        if (condition == null)
            condition = new Expr.Literal(true);
        body = new Stmt.While(condition, body);

        if (initializer != null) {
            body = new Stmt.Block(Arrays.asList(initializer, body));
        }

        return body;
    }

    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expect '(' after if.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after if condition.");

        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(ELSE)) {
            elseBranch = statement();
        }

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value");
        return new Stmt.Print(value);
    }

    private Stmt returnStatement() {
        Token keyword = previous();
        Expr value = null;
        if (!check(SEMICOLON)) {
            value = expression();
        }
        consume(SEMICOLON, "Expect ';' after return value.");
        return new Stmt.Return(keyword, value);
    }

    // parse var token
    private Stmt varDeclaration() {
        // parser has already matched the var token
        // consumes the identifier for variable name
        Token name = consume(IDENTIFIER, "Expect variable name");

        Expr initializer = null;
        // if it sees an = token it knows there is an initializer exp
        if (match(EQUAL)) {
            initializer = expression();
        }
        // consume the ;
        consume(SEMICOLON, "Expect ';' after variable declaration");
        // all gets wrapped in a Stmt.var syntax tree node.
        return new Stmt.Var(name, initializer);

    }

    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'while'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after condition.");
        Stmt body = statement();

        return new Stmt.While(condition, body);
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after expression");
        return new Stmt.Expression(expr);
    }

    private Stmt.Function function(String kind) {
        // consuming the function name
        Token name = consume(IDENTIFIER, "Expect " + kind + " name.");
        // consume the left paren
        consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");
        List<Token> paramaters = new ArrayList<>();
        // check to see if theres arguments
        if (!check(RIGHT_PAREN)) {
            // add parameters to list while the next token is ','
            do {
                if (paramaters.size() >= 255) {
                    error(peek(), "Can't have more than 255 parameters.");
                }

                paramaters.add(consume(IDENTIFIER, "Expect parameter name"));
            } while (match(COMMA));
        }
        // consume right paren
        consume(RIGHT_PAREN, "Expect ')' after parameters");
        consume(LEFT_BRACE, "Expect '{' before" + kind + " body.");
        List<Stmt> body = block();
        return new Stmt.Function(name, paramaters, body);
    }

    private List<Stmt> block() {
        // empty list
        List<Stmt> statements = new ArrayList<>();
        // parse statements until reach end of the block
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }
        // consume the '}'.
        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    private Expr expression() {
        return assigntment();
    }

    private Expr assigntment() {

        // parse the left side
        Expr expr = or();

        // match for "="
        if (match(EQUAL)) {
            // get the token
            Token equals = previous();
            // call assignment againt to parse the right side(its right-associative)
            Expr value = assigntment();

            // after parsing the left side check to see if it is a var
            if (expr instanceof Expr.Variable) {
                // downcast to Expr.variable and get the name
                Token name = ((Expr.Variable) expr).name;
                // return the syntax tree.
                return new Expr.Assign(name, value);
            } else if (expr instanceof Expr.Get) {
                Expr.Get get = (Expr.Get) expr;
                return new Expr.Set(get, equals, value);
            }

            error(equals, "Invalid assignmeent target.");
        }
        return expr;
    }

    private Expr or() {
        Expr expr = and();

        while (match(OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }
        return expr;
    }

    private Expr and() {
        Expr expr = equality();

        while (match(AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }
        return expr;
    }

    private Expr equality() {
        // comparison non terminal
        Expr expr = comparison();

        // matches the token to be != or == (consumes if true)
        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            // consumed token
            Token operator = previous();
            // right non terminal
            Expr right = comparison();
            // make a new bin expr using the old one in the left, the token as the operator
            // and the right
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr comparison() {
        Expr expr = term();
        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr term() {
        Expr expr = factor();
        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr factor() {
        Expr expr = unary();
        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr unary() {
        // look at current token see how to parse
        if (match(BANG, MINUS)) {
            // grab the token
            Token operator = previous();
            // recursively call unary() again to parse the operand.
            Expr right = unary();
            // put in unary expression syntax tree
            return new Expr.Unary(operator, right);
        }

        return call();
    }

    // arguments grammar rule translated to code
    private Expr finishCall(Expr calle) {
        List<Expr> arguments = new ArrayList<>();
        // handle the zero-argument case.
        // if next token is not a ')'
        if (!check(RIGHT_PAREN)) {
            // parse the expr and add it to the list of arguments
            // keep doing it as long as we find commas after each expr
            do {
                if (arguments.size() >= 255)
                    error(peek(), "Can't have more than 255 arguments");

                arguments.add(expression());
            } while (match(COMMA));
        }

        Token paren = consume(RIGHT_PAREN, "Expect ')' after arguments.");

        return new Expr.Call(calle, paren, arguments);
    }

    private Expr call() {
        // parse the primary expr
        Expr expr = primary();
        // each time we see a '(' we call finishCall() to parse the call expresion using
        // the previously parsed expresion as the callee.
        while (true) {
            if (match(LEFT_PAREN)) {
                // the returning expr becomes the new one and we loop to see if the result is
                // itself called.
                expr = finishCall(expr);
            } else if (match(DOT)) {
                Token name = consume(IDENTIFIER, "Expect property name after '.'");
                expr = new Expr.Get(expr, name);
            }

            else {
                break;
            }
        }

        return expr;
    }

    private Expr primary() {
        if (match(FALSE))
            return new Expr.Literal(false);
        if (match(TRUE))
            return new Expr.Literal(true);
        if (match(NIL))
            return new Expr.Literal(null);

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }
        if (match(SUPER)) {
            Token keyword = previous();
            consume(DOT, "Expect '.' after 'super'");
            Token method = consume(IDENTIFIER, "Expect superclass method name");
            return new Expr.Super(keyword, method);
        }
        if (match(THIS))
            return new Expr.This(previous());
        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        throw error(peek(), "Expect expression");
    }

    // check the current token has any of the given types, consumes the token
    // and returns true
    // or returns false and doesn't consume.
    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }

        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type))
            return advance();
        throw error(peek(), message);
    }

    // check if the current token is of argument type
    private boolean check(TokenType type) {
        if (isAtEnd())
            return false;
        return peek().type == type;
    }

    // advanced on the token list returns the consumed token
    private Token advance() {
        if (!isAtEnd())
            current++;
        return previous();
    }

    // check if is at end of the token list
    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    // peek the current token
    private Token peek() {
        return tokens.get(current);
    }

    // return the consumed token
    private Token previous() {
        return tokens.get(current - 1);
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    // advance until a new statement or declaration its found
    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if (previous().type == SEMICOLON)
                return;

            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }

            advance();
        }
    }

}
