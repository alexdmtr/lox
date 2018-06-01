package com.craftinginterpreters.lox;

import java.util.List;

class LoxFunction implements LoxCallable {
  static class Declaration {
    final List<Token> parameters;
    final List<Stmt> body;
    final String name;

    Declaration(String name, List<Token> parameters, List<Stmt> body) {
      this.name = name;
      this.parameters = parameters;
      this.body = body;
    }
  }
  static enum Kind {
    NAMED, ANONYMOUS
  }

  final Kind kind;
  private final Declaration declaration;
  private final Environment closure;
  LoxFunction(Stmt.Function declaration, Environment closure) {
    this.declaration = new Declaration(declaration.name.lexeme, declaration.parameters, declaration.body);
    this.closure = closure;
    this.kind = Kind.NAMED;
  }

  // Overload for receiving function expressions (anonymous methods)
  LoxFunction(Expr.Function declaration, Environment closure) {
    this.kind = declaration.kind;
    this.declaration = new Declaration(kind != Kind.ANONYMOUS ? declaration.name.lexeme : "", declaration.parameters, declaration.body);
    this.closure = closure;
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
}