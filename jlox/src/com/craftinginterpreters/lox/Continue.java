package com.craftinginterpreters.lox;

public class Continue extends RuntimeException {
  Continue() {
    super(null, null, false, false);
  }
}
