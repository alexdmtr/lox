package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// This class is modified so that statements *do* return values
class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Object> {
  final Environment globals = new Environment();
  private Environment environment = globals;
  private final Map<Expr, Integer> locals = new HashMap<>();

  Interpreter() {
    globals.define("NEWLINE_CHAR", "\n");
    globals.define("clock", new LoxCallable() {
      @Override
      public int arity() {
        return 0;
      }

      @Override
      public Object call(Interpreter interpreter,
                         List<Object> arguments) {
        return (double)System.currentTimeMillis() / 1000.0;
      }
    });

    java.util.Scanner scanner = new java.util.Scanner(System.in);
    java.util.Scanner charScanner = new java.util.Scanner(System.in);
    charScanner.useDelimiter("");

    globals.define("readDouble", new LoxCallable() {
      @Override
      public int arity() {
        return 0;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments) {
        double value = scanner.nextDouble();

        return value;
      }
    });

    globals.define("readInt", new LoxCallable() {

      @Override
      public int arity() {
        return 0;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments) {
        double value = scanner.nextInt();

        return value;
      }
    });

    globals.define("readLine", new LoxCallable() {

      @Override
      public int arity() {
        return 0;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments) {
        String value = scanner.nextLine();

        return value;
      }
    });

    globals.define("readByte", new LoxCallable() {

      @Override
      public int arity() {
        return 0;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments) {
        String value = charScanner.next();

        return value;
      }
    });
  }

  List<Object> interpret(List<Stmt> statements) {
    List<Object> results = new ArrayList<>();
    try {
      for (Stmt statement : statements) {
        results.add(execute(statement));
      }
    } catch (RuntimeError error) {
      Lox.runtimeError(error);
    }
    return results;
  }

  private Object execute(Stmt stmt) {
    return stmt.accept(this);
  }

  void resolve(Expr expr, int depth) {
    locals.put(expr, depth);
  }

  void executeBlock(List<Stmt> statements, Environment environment) {
    Environment previous = this.environment;
    try {
      this.environment = environment;

      for (Stmt statement : statements) {
        execute(statement);
      }
    } finally {
      this.environment = previous;
    }
  }

  @Override
  public Void visitBlockStmt(Stmt.Block stmt) {
    executeBlock(stmt.statements, new Environment(environment));
    return null;
  }

  @Override
  public Void visitClassStmt(Stmt.Class stmt) {
    environment.define(stmt.name.lexeme, null);
    LoxClass klass = new LoxClass(stmt.name.lexeme);
    environment.assign(stmt.name, klass);
    return null;
  }

  @Override
  public Object visitFunctionExpr(Expr.Function expr) {
    LoxFunction function = new LoxFunction(expr, environment);
    if (function.kind == LoxFunction.Kind.NAMED) {
      try {
      environment.define(expr.name.lexeme, function);
    } catch (Environment.RedefineVariableError variableError) {
      throw new Environment.RedefineVariableError(expr.name, variableError.getMessage());
    } // Environment doesn't know the token we're redefining a variable at.
    // So, we catch it here and throw an identical error, but with the token defined.
    }

    return function;
  }

  @Override
  public Object visitLiteralExpr(Expr.Literal expr) {
    return expr.value;
  }

  @Override
  public Object visitLogicalExpr(Expr.Logical expr) {
    Object left = evaluate(expr.left);

    if (expr.operator.type == TokenType.OR) {
      if (isTruthy(left)) return left;
    } else if (expr.operator.type == TokenType.AND){
      if (!isTruthy(left)) return left;
    }

    return evaluate(expr.right);
  }

  @Override
  public Object visitSetExpr(Expr.Set expr) {
    Object object = evaluate(expr.object);

    if (!(object instanceof LoxInstance)) {
      throw new RuntimeError(expr.name, "Only instances have fields.");
    }

    Object value = evaluate(expr.value);
    ((LoxInstance)object).set(expr.name, value);
    return value;
  }

  @Override
  public Object visitUnaryExpr(Expr.Unary expr) {
    Object right = evaluate(expr.right);

    switch (expr.operator.type) {
      case MINUS:
        checkNumberOperand(expr.operator, right);
        return -(double)right;
      case BANG:
        return !isTruthy(right);
    }

    // Unreachable.
    return null;
  }

  @Override
  public Object visitVariableExpr(Expr.Variable expr) {
    return lookUpVariable(expr.name, expr);
  }

  private Object lookUpVariable(Token name, Expr expr) {
    Integer distance = locals.get(expr);
    if (distance != null) {
      return environment.getAt(distance, name.lexeme);
    } else {
      return globals.get(name);
    }
  }

  private void checkNumberOperand(Token operator, Object operand) {
    if (operand instanceof Double) return;
    throw new RuntimeError(operator, "Operand must be a number.");
  }

  private void checkNumberOperands(Token operator,
                                   Object left, Object right) {
    if (left instanceof Double && right instanceof Double) return;

    throw new RuntimeError(operator, "Operands must be numbers.");
  }

  private boolean isTruthy(Object object) {
    if (object == null) return false;
    if (object instanceof Boolean) return (boolean)object;
    if (object instanceof Double) return (double)object != 0;
    return true;
  }

  @Override
  public Object visitBinaryExpr(Expr.Binary expr) {
    Object left = evaluate(expr.left);
    Object right = evaluate(expr.right);

    switch (expr.operator.type) {
      case MINUS:
        checkNumberOperands(expr.operator, left, right);
        return (double)left - (double)right;
      case SLASH:
        checkNumberOperands(expr.operator, left, right);
        return (double)left / (double)right;
      case STAR:
        checkNumberOperands(expr.operator, left, right);
        return (double)left * (double)right;
      case PERCENT:
        checkNumberOperands(expr.operator, left, right);
        return (double)left % (double)right;
      case PLUS:
        if (left instanceof Double && right instanceof Double) {
          return (double)left + (double)right;
        }

        if (left instanceof String || right instanceof String) {
          return stringify(left) + stringify(right);
        }

        throw new RuntimeError(expr.operator,
            "Operands must be two numbers or one of them must be a string.");
      case GREATER:
        return compare(expr, left, right) > 0;
      case GREATER_EQUAL:
        return compare(expr, left, right) >= 0;
      case LESS:
        return compare(expr, left, right) < 0;
      case LESS_EQUAL:
        return compare(expr, left, right) <= 0;
      case BANG_EQUAL: return !isEqual(left, right);
      case EQUAL_EQUAL: return isEqual(left, right);

      // To do: make this an enumeration?
      case COMMA: return right;
    }

    // Unreachable.
    return null;
  }

  @Override
  public Object visitCallExpr(Expr.Call expr) {
    Object callee = evaluate(expr.callee);

    List<Object> arguments = new ArrayList<>();
    for (Expr argument : expr.arguments) {
      arguments.add(evaluate(argument));
    }

    if (!(callee instanceof LoxCallable)) {
      throw new RuntimeError(expr.paren,
          "Can only call functions and classes.");
    }

    LoxCallable function = (LoxCallable)callee;

    if (arguments.size() != function.arity()) {
      throw new RuntimeError(expr.paren, "Expected " +
          function.arity() + " arguments but got " +
          arguments.size() + ".");
    }

    return function.call(this, arguments);
  }

  @Override
  public Object visitGetExpr(Expr.Get expr) {
    Object object = evaluate(expr.object);
    if (object instanceof LoxInstance) {
      return ((LoxInstance) object).get(expr.name);
    }

    throw new RuntimeError(expr.name,
        "Only instances have properties.");
  }

  // This is added by me, so we can compare strings lexicographically.
  private int compare(Expr.Binary expr, Object left, Object right) {
    if (left instanceof Double && right instanceof Double)
      return (int)((double)left - (double)right);
    if (left instanceof String && right instanceof String)
      return ((String) left).compareTo((String) right);

    throw new RuntimeError(expr.operator,
        "Operands must be two numbers or two strings.");
  }

  private boolean isEqual(Object a, Object b) {
    // nil is only equal to nil.
    if (a == null && b == null) return true;
    if (a == null) return false;

    boolean result = a.equals(b);

    return result;
  }

  String stringify(Object object) {
    if (object == null) return "nil";

    // Hack. Work around Java adding ".0" to integer-valued doubles.
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

  @Override
  public Object visitBreakStmt(Stmt.Break stmt) {
    throw new Break();
  }

  @Override
  public Object visitContinueStmt(Stmt.Continue stmt) {
    throw new Continue();
  }

  @Override
  public Object visitExpressionStmt(Stmt.Expression stmt) {
    Object value = evaluate(stmt.expression);
    return value;
  }

  @Override
  public Void visitFunctionStmt(Stmt.Function stmt) {
    LoxFunction function = new LoxFunction(stmt, environment);
    environment.define(stmt.name.lexeme, function);
    return null;
  }

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
  public Object visitPrintStmt(Stmt.Print stmt) {
    Object value = evaluate(stmt.expression);
    System.out.println(stringify(value));
    return value;
  }

  @Override
  public Void visitReturnStmt(Stmt.Return stmt) {
    Object value = null;
    if (stmt.value != null) value = evaluate(stmt.value);

    throw new Return(value); // Clever, haha.
  }

  @Override
  public Void visitVarStmt(Stmt.Var stmt) {
    Object value = null;
    try {
      if (stmt.initializer != null) {
        value = evaluate(stmt.initializer);
        environment.define(stmt.name.lexeme, value);
      } else {
        environment.define(stmt.name.lexeme);
      }
    } catch (Environment.RedefineVariableError variableError) {
      throw new Environment.RedefineVariableError(stmt.name, variableError.getMessage());
    } // Environment doesn't know the token we're redefining a variable at.
      // So, we catch it here and throw an identical error, but with the token defined.
    return null;
  }

  @Override
  public Void visitWhileStmt(Stmt.While stmt) {
    while (isTruthy(evaluate(stmt.condition))) {
      try {
        execute(stmt.body);
      } catch (Break breakException) {
        break;
      } catch (Continue continueException) {
        continue;
      }
    }
    return null;
  }

  @Override
  public Object visitAssignExpr(Expr.Assign expr) {
    Object value = evaluate(expr.value);

    Integer distance = locals.get(expr);
    if (distance != null) {
      environment.assignAt(distance, expr.name, value);
    } else {
      globals.assign(expr.name, value);
    }
    return value;
  }
}