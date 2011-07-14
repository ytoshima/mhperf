package name.yt;

import java.util.concurrent.ConcurrentHashMap;
import java.lang.invoke.*;
import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.*;


public class SwitchPointEx {
  public static void main(String... args) throws Throwable {
    MethodHandles.Lookup lookup = lookup();
    MethodType type = 
        methodType(IRubyObject.class, ThreadContext.class);
    RubyConstantCallSite site = new RubyConstantCallSite(type, "site1");
    ThreadContext tc = new ThreadContext();
    tc.setConstant("site1", new IRubyObject("Foo"));
    D("set value to Foo");
    MethodType fallbackType = type.insertParameterTypes(0, RubyConstantCallSite.class);
    MethodHandle myFallback = insertArguments(
                lookup.findStatic(SwitchPointEx.class, "constantFallback",
                fallbackType),
                0,
                site);
    site.setTarget(myFallback);

    IRubyObject iro = null;
    iro = (IRubyObject)site.getTarget().invokeExact(tc);
    D("callsite invoke 1: " + iro);
    iro = (IRubyObject)site.getTarget().invokeExact(tc);
    D("callsite invoke 2: " + iro);
    iro = (IRubyObject)site.getTarget().invokeExact(tc);
    D("callsite invoke 3: " + iro);
    tc.runtime.getConstantInvalidator().invalidate();
    D("invalidated");
    tc.setConstant("site1", new IRubyObject("Bar"));
    D("set value to Bar");
    iro = (IRubyObject)site.getTarget().invokeExact(tc);
    D("callsite invoke 4: " + iro);
    iro = (IRubyObject)site.getTarget().invokeExact(tc);
    D("callsite invoke 5: " + iro);
    iro = (IRubyObject)site.getTarget().invokeExact(tc);
    D("callsite invoke 6: " + iro);
  }

  public static IRubyObject constantFallback(RubyConstantCallSite site,
      ThreadContext context) {
    IRubyObject value = context.getConstant(site.name());
  
    if (value != null) {
      //if (RubyInstanceConfig.LOG_INDY_CONSTANTS) LOG.info("constant " + site.name() + " bound directly");
      D("In constantFallback value=" + value); 
      MethodHandle valueHandle = constant(IRubyObject.class, value);
      valueHandle = dropArguments(valueHandle, 0, ThreadContext.class);

      MethodHandle fallback = insertArguments(
          findStatic(SwitchPointEx.class, "constantFallback",
          methodType(IRubyObject.class, RubyConstantCallSite.class, ThreadContext.class)),
          0,
          site);

      SwitchPoint switchPoint = (SwitchPoint)context.runtime.getConstantInvalidator().getData();
      MethodHandle gwt = switchPoint.guardWithTest(valueHandle, fallback);
      site.setTarget(gwt);
    } else {
      D("In constantFallback value=null");
      //value = context.getCurrentScope().getStaticScope().getModule()
      //    .callMethod(context, "const_missing", context.getRuntime().fastNewSymbol(site.name()));
      value = new IRubyObject("(null)");
    }

    return value;
  }

  private static MethodHandle findStatic(Class target, String name, MethodType type) {
    try {
      return lookup().findStatic(target, name, type);
    } catch (NoSuchMethodException nsme) {
      throw new RuntimeException(nsme);
    } catch (IllegalAccessException nae) {
      throw new RuntimeException(nae);
    }
  }


  public static void D(Object o) { System.out.println("D: " + o); }
}

class IRubyObject {
  private Object value;
  public IRubyObject(Object value) { this.value = value; }
  public Object getValue() { return value; }
  public String toString() { return "IRubyObject{"+value+"}";}
}

class ThreadContext {
  private ConcurrentHashMap<String,IRubyObject> map = new ConcurrentHashMap<>();
  public IRubyObject getConstant(String key) {
    return map.get(key);
  }
  public void setConstant(String key, IRubyObject value) {
    map.put(key, value);
  }
  MyRuntime runtime = new MyRuntime();
}

class ConstantInvalidator {
  SwitchPoint lastInstance;
  SwitchPoint getData() { lastInstance = new SwitchPoint(); return lastInstance; }
  void invalidate() { SwitchPoint.invalidateAll(new SwitchPoint[] {lastInstance}); }
}

class MyRuntime {
  ConstantInvalidator constantInvalidator = new ConstantInvalidator();
  ConstantInvalidator getConstantInvalidator() { return constantInvalidator; } 
}

class RubyConstantCallSite extends MutableCallSite {
  private String name;
  public RubyConstantCallSite(MethodType type, String name) {
    super(type);
    setName(name);
  }
  public void setName(String name) { this.name = name; }
  public String name() {
    return this.name;
  }
}
