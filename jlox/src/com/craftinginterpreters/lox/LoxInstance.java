package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.Map;

class LoxInstance {
  protected LoxClass klass;
  private final Map<String, Object> fields = new HashMap<>();

  LoxInstance(LoxClass klass) {
    this.klass = klass;
  }

  Object get(Token name) {
    if (fields.containsKey(name.lexeme)) {
      return fields.get(name.lexeme);
    }

    LoxCallable method = klass.findMethod(this, name.lexeme);
    if (method != null) return method;

    throw new RuntimeError(name,
        "Undefined property '" + name.lexeme + "'.");
  }

  Object get(String name) {
    if (fields.containsKey(name)) {
      return fields.get(name);
    }

    LoxCallable method = klass.findMethod(this, name);
    if (method != null) return method;

    throw new RuntimeError(null,
        "Undefined property '" + name + "'.");
  }

  void set(Token name, Object value) {
    fields.put(name.lexeme, value);
  }
  void set(String name, Object value) { fields.put(name, value); }

  @Override
  public String toString() {
    return klass.name + " instance";
  }

  public boolean isInstanceOf(LoxClass classType) {
    LoxClass klass = this.klass;

    // Go up the inheritance chain.
    while (klass != null) {
      if (klass == classType)
        return true;

      klass = klass.superclass;
    }

    return false;
  }
}