# Lox grammar

program → declaration\* EOF ;

# Declarations

## A program is a series of declarations, which are the statements that bind new identifiers or any of the other statement types.

declaration → classDecl
| funDecl
| varDecl
| statement ;

classDecl → "class" IDENTIFIER ( "<" IDENTIFIER )?
"{" function\* "}" ;
funDecl → "fun" function ;
varDecl → "var" IDENTIFIER ( "=" expression )? ";" ;

# Statements

## The remaining statement rules produce side effects, but do not introduce bindings.

statement → exprStmt
| forStmt
| ifStmt
| printStmt
| returnStmt
| whileStmt
| block ;

exprStmt → expression ";" ;
forStmt → "for" "(" ( varDecl | exprStmt | ";" )
expression? ";"
expression? ")" statement ;
ifStmt → "if" "(" expression ")" statement
( "else" statement )? ;
printStmt → "print" expression ";" ;
returnStmt → "return" expression? ";" ;
whileStmt → "while" "(" expression ")" statement ;
block → "{" declaration\* "}" ;

# Expressions

## Expressions produce values. Lox has a number of unary and binary operators with different levels of precedence. Some grammars for languages do not directly encode the precedence relationships and specify that elsewhere. Here, we use a separate rule for each precedence level to make it explicit.

expression → assignment ;

assignment → ( call "." )? IDENTIFIER "=" assignment
| logic_or ;

logic*or → logic_and ( "or" logic_and )* ;
logic*and → equality ( "and" equality )* ;
equality → comparison ( ( "!=" | "==" ) comparison )_ ;
comparison → term ( ( ">" | ">=" | "<" | "<=" ) term )_ ;
term → factor ( ( "-" | "+" ) factor )_ ;
factor → unary ( ( "/" | "_" ) unary )\* ;

unary → ( "!" | "-" ) unary | call ;
call → primary ( "(" arguments? ")" | "." IDENTIFIER )\* ;
primary → "true" | "false" | "nil" | "this"
| NUMBER | STRING | IDENTIFIER | "(" expression ")"
| "super" "." IDENTIFIER ;

# Utility rules

function → (IDENTIFIER)? "(" parameters? ")" block ;
parameters → IDENTIFIER ( "," IDENTIFIER )_ ;
arguments → expression ( "," expression )_ ;
