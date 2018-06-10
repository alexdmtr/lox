package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LoxHashMap extends LoxClass {
  LoxHashMap() {
    super("HashMap", null, new HashMap<>(), new HashMap<>());
  }

  @Override
  public Object call(Interpreter interpreter, List<Object> arguments) {
    LoxInstance instance = new LoxInstance(this);
    instance.set("map", new HashMap<String, Object>());

    return instance;
  }


  @Override
  LoxCallable findMethod(LoxInstance instance, String name) {
    switch (name) {
      case "put":
        return new LoxCallable() {
          @Override
          public Object call(Interpreter interpreter, List<Object> arguments) {
            String key = (String)arguments.get(0);
            Object value = arguments.get(1);

            Map<String, Object> map = (Map<String, Object>)instance.get("map");
            return map.put(key, value);
          }

          @Override
          public int arity() {
            return 2;
          }
        };
      case "get":
        return new LoxCallable() {
          @Override
          public Object call(Interpreter interpreter, List<Object> arguments) {
            String key = (String)arguments.get(0);

            Map<String, Object> map = (Map<String, Object>)instance.get("map");
            return map.get(key);
          }

          @Override
          public int arity() {
            return 1;
          }
        };
      default:
        return LoxFunction.getNOOP(false);
    }
  }
}
