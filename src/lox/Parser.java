package lox;

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

    Expr parse() {
        try {
            return expression();
        } catch (ParseError error) {
            return null;
        }
    }

    private Expr expression() {
        return equality();
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

        return primary();
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

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        throw error(peek(), "Expect expression");
    }

    // check the current token has any of the given types, consumes the token and
    // returns true or returns false and doesn't consume.
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
