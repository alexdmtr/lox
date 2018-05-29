package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;

// This class is modified so that statements *do* return values
class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Object> {
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

  @Override
  public Object visitLiteralExpr(Expr.Literal expr) {
    return expr.value;
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
    }

    // Unreachable.
    return null;
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

    return a.equals(b);
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
  public Object visitExpressionStmt(Stmt.Expression stmt) {
    Object value = evaluate(stmt.expression);
    return value;
  }

  @Override
  public Object visitPrintStmt(Stmt.Print stmt) {
    Object value = evaluate(stmt.expression);
    System.out.println(stringify(value));
    return value;
  }
}