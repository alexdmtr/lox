package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class Environment {
  static class RedefineVariableError extends RuntimeError {
    RedefineVariableError(Token token, String message) {
      super(token, message);
    }
  }

  final Environment enclosing;
  private final Map<String, Object> values = new HashMap<>();
  // Checks whether a variable has been previously assigned a value
  private final Set<String> initializedVariables = new HashSet<>();

  Environment() {
    enclosing = null;
  }

  Environment(Environment enclosing) {
    this.enclosing = enclosing;
  }

  // Throw error if redefining scope variables.
  private void assertNotDefined(String name) {
    if (values.containsKey(name))
      throw new RedefineVariableError(null, "Attempting to redefine scope variable '" + name + "'.");
  }

  // Create a variable without assigning to it.
  void define(String name) {
    assertNotDefined(name);
    values.put(name, null);
  }

  void define(String name, Object value) {
    assertNotDefined(name);
    values.put(name, value);
    initializedVariables.add(name);
  }

  Object get(Token name) {
    if (values.containsKey(name.lexeme)) {

      // Assert this variable has been assigned to first!
      if (!initializedVariables.contains(name.lexeme))
        throw new RuntimeError(name, "Attempting to access uninitialized variable '" + name.lexeme + "'.");
      return values.get(name.lexeme);
    }

    if (enclosing != null) return enclosing.get(name);

    throw new RuntimeError(name,
        "Undefined variable '" + name.lexeme + "'.");
  }

  void assign(Token name, Object value) {
    if (values.containsKey(name.lexeme)) {
      values.put(name.lexeme, value);
      initializedVariables.add(name.lexeme);
      return;
    }

    if (enclosing != null) {
      enclosing.assign(name, value);
      return;
    }

    throw new RuntimeError(name,
        "Undefined variable '" + name.lexeme + "'.");
  }
}