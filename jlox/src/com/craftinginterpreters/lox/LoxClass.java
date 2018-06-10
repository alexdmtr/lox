package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class LoxClass extends LoxInstance implements LoxCallable {
  final String name;
  final LoxClass superclass;
  private final Map<String, LoxFunction> methods;
  private final static LoxClass MetaClass;

  static {
    MetaClass = new LoxClass("MetaClass", new HashMap<>());
  }

  // Only used for meta classes.
  private LoxClass(String name, Map<String, LoxFunction> staticMethods) {
    super(null);
    this.name = name;
    this.methods = staticMethods;
    this.superclass = null;
  }

  LoxClass(String name, LoxClass superclass, Map<String, LoxFunction> methods, Map<String, LoxFunction> staticMethods) {
    super(MetaClass);
    this.name = name;
    this.methods = methods;
    this.superclass = superclass;
    this.klass = new LoxClass(name + " (metaclass)" , staticMethods);

    // Create NOOP initializer, if otherwise there's none.
    if (!methods.containsKey("init"))
      methods.put("init", LoxFunction.getNOOP(true));
  }

  LoxCallable findMethod(LoxInstance instance, String name) {
    if (methods.containsKey(name)) {
      return methods.get(name).bind(instance);
    }

    if (superclass != null) {
      return superclass.findMethod(instance, name);
    }

    return null;
  }

  LoxCallable findStaticMethod(String name) {
    LoxClass metaClass = klass;
    return metaClass.findMethod(this, name);
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public Object call(Interpreter interpreter, List<Object> arguments) {
    LoxInstance instance = new LoxInstance(this);
    LoxFunction initializer = methods.get("init");
    if (initializer != null) {
      initializer.bind(instance).call(interpreter, arguments);
    }

    return instance;
  }

  @Override
  public int arity() {
    LoxFunction initializer = methods.get("init");
    if (initializer == null) return 0;
    return initializer.arity();
  }
}