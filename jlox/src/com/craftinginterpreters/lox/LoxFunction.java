package com.craftinginterpreters.lox;

import java.util.List;

class LoxFunction implements LoxCallable {
  static class Declaration {
    final List<Token> parameters;
    final List<Stmt> body;
    final String name;
    final Kind kind;

    Declaration(Kind kind, String name, List<Token> parameters, List<Stmt> body) {
      this.kind = kind;
      this.name = name;
      this.parameters = parameters;
      this.body = body;
    }
  }
  static enum Kind {
    NAMED, ANONYMOUS
  }

  private final Declaration declaration;
  private final Environment closure;
  LoxFunction(Stmt.Function declaration, Environment closure) {
    this.declaration = new Declaration(Kind.NAMED, declaration.name.lexeme, declaration.parameters, declaration.body);
    this.closure = closure;
  }

  // Overload for receiving function expressions (anonymous methods)
  LoxFunction(Expr.Function declaration, Environment closure) {
    this.declaration = new Declaration(declaration.kind, declaration.kind != Kind.ANONYMOUS ? declaration.name.lexeme : "", declaration.parameters, declaration.body);
    this.closure = closure;
  }

  // Private overload for receiving a declaration object
  private LoxFunction(Declaration declaration, Environment closure) {
    this.declaration = declaration;
    this.closure = closure;
  }

  LoxFunction bind(LoxInstance instance) {
    Environment environment = new Environment(closure);
    environment.define("this", instance);
    return new LoxFunction(declaration, environment);
  }

  @Override
  public int arity() {
    return declaration.parameters.size();
  }

  @Override
  public Object call(Interpreter interpreter, List<Object> arguments) {
    Environment environment = new Environment(closure);
    for (int i = 0; i < declaration.parameters.size(); i++) {
      environment.define(declaration.parameters.get(i).lexeme,
          arguments.get(i));
    }

    try {
      interpreter.executeBlock(declaration.body, environment);
    } catch (Return returnValue) {
      return returnValue.value;
    }

    return null;
  }

  @Override
  public String toString() {
    return "<fn " + declaration.name + ">";
  }

  public Kind getKind() {
    return this.declaration.kind;
  }
}