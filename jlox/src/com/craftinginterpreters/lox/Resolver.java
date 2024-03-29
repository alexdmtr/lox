package com.craftinginterpreters.lox;

import java.util.*;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
  private final Interpreter interpreter;
  private final Stack<Map<String, Boolean>> scopes = new Stack<>();


  private enum FunctionType {
    NONE,
    FUNCTION,
    METHOD,
    INITIALIZER
  }
  private FunctionType currentFunction = FunctionType.NONE;

  private enum LoopType {
    NONE,
    WHILE
  }
  private LoopType currentLoop = LoopType.NONE;

  private enum ClassType {
    NONE,
    CLASS,
    SUBCLASS
  }

  private enum FunctionContext {
    DYNAMIC,
    STATIC
  }

  private FunctionContext currentContext = FunctionContext.DYNAMIC;

  private ClassType currentClass = ClassType.NONE;

  Resolver(Interpreter interpreter) {
    this.interpreter = interpreter;
  }

  @Override
  public Void visitBlockStmt(Stmt.Block stmt) {
    beginScope();
    resolve(stmt.statements);
    endScope();
    return null;
  }

  @Override
  public Void visitBreakStmt(Stmt.Break stmt) {
    if (currentLoop == LoopType.NONE)
      Lox.error(stmt.name, "Cannot break from outside a loop.");
    return null;
  }

  @Override
  public Void visitClassStmt(Stmt.Class stmt) {
    declare(stmt.name);
    define(stmt.name);
    ClassType enclosingClass = currentClass;
    currentClass = ClassType.CLASS;

    if (stmt.superclass != null) {
      currentClass = ClassType.SUBCLASS;
      resolve(stmt.superclass);
      beginScope();
      scopes.peek().put("super", true);
    }

    beginScope();
    scopes.peek().put("this", true);

    for (Stmt.Function method : stmt.methods) {
      FunctionType declaration = FunctionType.METHOD;
      if (method.name.lexeme.equals("init")) {
        declaration = FunctionType.INITIALIZER;
      }
      resolveFunction(method, declaration, FunctionContext.DYNAMIC);
    }
    for (Stmt.Function method : stmt.staticMethods) {
      FunctionType declaration = FunctionType.METHOD;
      if (method.name.lexeme.equals("init")) {
        declaration = FunctionType.INITIALIZER;
      }
      resolveFunction(method, declaration, FunctionContext.STATIC);
    }

    endScope();

    if (stmt.superclass != null) endScope();

    currentClass = enclosingClass;

    return null;
  }


  @Override
  public Void visitContinueStmt(Stmt.Continue stmt) {
    if (currentLoop == LoopType.NONE)
      Lox.error(stmt.name, "Cannot continue from outside a loop.");
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

    resolveFunction(stmt, FunctionType.FUNCTION, FunctionContext.STATIC);
    return null;
  }

  @Override
  public Void visitIfStmt(Stmt.If stmt) {
    resolve(stmt.condition);
    resolve(stmt.thenBranch);
    if (stmt.elseBranch != null) resolve(stmt.elseBranch);
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
      Lox.error(stmt.keyword, "Cannot return from top-level code.");
    }

    if (stmt.value != null) {
      if (currentFunction == FunctionType.INITIALIZER) {
        Lox.error(stmt.keyword,
            "Cannot return a value from an initializer.");
      }
      resolve(stmt.value);
    }

    return null;
  }

  @Override
  public Void visitSuperExpr(Expr.Super expr) {
    if (currentClass == ClassType.NONE) {
      Lox.error(expr.keyword,
          "Cannot use 'super' outside of a class.");
    } else if (currentClass != ClassType.SUBCLASS) {
      Lox.error(expr.keyword,
          "Cannot use 'super' in a class with no superclass.");
    }
    resolveLocal(expr, expr.keyword);
    return null;
  }

  @Override
  public Void visitVarStmt(Stmt.Var stmt) {
    declare(stmt.name);
    if (stmt.initializer != null) {
      resolve(stmt.initializer);
    }
    define(stmt.name);
    return null;
  }

  @Override
  public Void visitWhileStmt(Stmt.While stmt) {
    resolve(stmt.condition);

    LoopType enclosingLoop = currentLoop;
    currentLoop = LoopType.WHILE;

    resolve(stmt.body);

    currentLoop = enclosingLoop;
    return null;
  }

  @Override
  public Void visitAssignExpr(Expr.Assign expr) {
    resolve(expr.value);
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
  public Void visitFunctionExpr(Expr.Function expr) {
    if (expr.kind == LoxFunction.Kind.NAMED) {
      declare(expr.name);
      define(expr.name);
    }

    resolveFunction(expr, FunctionType.FUNCTION, FunctionContext.STATIC);
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
  public Void visitThisExpr(Expr.This expr) {
    if (currentClass == ClassType.NONE) {
      Lox.error(expr.keyword,
          "Cannot use 'this' outside of a class.");
      return null;
    }
    if (currentContext == FunctionContext.STATIC)
      Lox.error(expr.keyword,
          "Cannot use 'this' in a static context.");

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
    if (!scopes.isEmpty() &&
        scopes.peek().get(expr.name.lexeme) == Boolean.FALSE) {
      Lox.error(expr.name,
          "Cannot read local variable in its own initializer.");
    }

    resolveLocal(expr, expr.name);
    return null;
  }

  void resolve(List<Stmt> statements) {
    for (Stmt statement : statements) {
      resolve(statement);
    }
  }

  private void resolve(Stmt stmt) {
    stmt.accept(this);
  }

  private void resolve(Expr expr) {
    expr.accept(this);
  }

  private void resolveFunction(Object function, FunctionType type, FunctionContext context) {
    FunctionType enclosingFunction = currentFunction;
    currentFunction = type;
    FunctionContext enclosingContext = currentContext;
    currentContext = context;
    List<Token> parameters;
    List<Stmt> body;


    if (function instanceof Stmt.Function) {
      parameters = ((Stmt.Function) function).parameters;
      body = ((Stmt.Function) function).body;


      if (currentFunction == FunctionType.INITIALIZER && currentContext == FunctionContext.STATIC
          && parameters.size() > 0)
        Lox.error(((Stmt.Function) function).name, "Static initializers cannot have parameters.");
    } else if (function instanceof Expr.Function) {
      parameters = ((Expr.Function) function).parameters;
      body = ((Expr.Function) function).body;

      if (currentFunction == FunctionType.INITIALIZER && currentContext == FunctionContext.STATIC
          && parameters.size() > 0)
        Lox.error(((Expr.Function) function).name, "Static initializers cannot have parameters.");
    } else {
      parameters = new ArrayList<>();
      body = new ArrayList<>();
    }

    beginScope();
    for (Token param : parameters) {
      declare(param);
      define(param);
    }
    resolve(body);
    endScope();

    currentFunction = enclosingFunction;
    currentContext = enclosingContext;
  }

  private void beginScope() {
    scopes.push(new HashMap<>());
  }

  private void endScope() {
    scopes.pop();
  }

  private void declare(Token name) {
    if (scopes.isEmpty()) return;

    Map<String, Boolean> scope = scopes.peek();
    if (scope.containsKey(name.lexeme)) {
      Lox.error(name,
          "Variable with this name already declared in this scope.");
    }
    scope.put(name.lexeme, false); // false - not ready yet
  }

  private void define(Token name) {
    if (scopes.isEmpty()) return;
    scopes.peek().put(name.lexeme, true);
  }

  private void resolveLocal(Expr expr, Token name) {
    for (int i = scopes.size() - 1; i >= 0; i--) {
      if (scopes.get(i).containsKey(name.lexeme)) {
        interpreter.resolve(expr, scopes.size() - 1 - i); // the "distance" to travel through environment chain
        return;
      }
    }

    // Not found. Assume it is global.
  }
}