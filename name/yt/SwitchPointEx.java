package name.yt;

import java.util.concurrent.ConcurrentHashMap;
import java.lang.invoke.*;
import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.*;

/*

An experiment of SwitchPoint.

In main, a custum call site is created and constantFallback + call site argument
is set to its target.  

When the call site's target was called for the first time, constantFallback
creates a constant MethodHandle.  It then creates a SwitchPoint which takes
the constant method handle and the constantFallback method handle.

Until the switch point is invalidated, the call site target call would invoke
the constant method handle.  After the switch point invalidation, constantFallback
is called and setup a constant method handle with current value and setup
SwichPoint again.  Following call would return the new constant value.

In this way, the constant can be obtained quickly, without polling the change.

Sample output:

D: set value to Foo
D: In constantFallback value=IAmethystObject{Foo}
D: callsite invoke 1: IAmethystObject{Foo}
D: callsite invoke 2: IAmethystObject{Foo}
D: callsite invoke 3: IAmethystObject{Foo}
D: invalidated
D: set value to Bar
D: In constantFallback value=IAmethystObject{Bar}
D: callsite invoke 4: IAmethystObject{Bar}
D: callsite invoke 5: IAmethystObject{Bar}
D: callsite invoke 6: IAmethystObject{Bar}

*/


public class SwitchPointEx {
  public static void main(String... args) throws Throwable {
    MethodHandles.Lookup lookup = lookup();
    MethodType type = 
        methodType(IAmethystObject.class, ThreadContext.class);
    AmethystConstantCallSite site = new AmethystConstantCallSite(type, "site1");
    ThreadContext tc = new ThreadContext();
    tc.setConstant("site1", new IAmethystObject("Foo"));
    D("set value to Foo");
    MethodType fallbackType = type.insertParameterTypes(0, AmethystConstantCallSite.class);
    MethodHandle myFallback = insertArguments(
                lookup.findStatic(SwitchPointEx.class, "constantFallback",
                fallbackType),
                0,
                site);
    site.setTarget(myFallback);

    IAmethystObject iro = null;
    iro = (IAmethystObject)site.getTarget().invokeExact(tc);
    D("callsite invoke 1: " + iro);
    iro = (IAmethystObject)site.getTarget().invokeExact(tc);
    D("callsite invoke 2: " + iro);
    iro = (IAmethystObject)site.getTarget().invokeExact(tc);
    D("callsite invoke 3: " + iro);
    tc.runtime.getConstantInvalidator().invalidate();
    D("invalidated");
    tc.setConstant("site1", new IAmethystObject("Bar"));
    D("set value to Bar");
    iro = (IAmethystObject)site.getTarget().invokeExact(tc);
    D("callsite invoke 4: " + iro);
    iro = (IAmethystObject)site.getTarget().invokeExact(tc);
    D("callsite invoke 5: " + iro);
    iro = (IAmethystObject)site.getTarget().invokeExact(tc);
    D("callsite invoke 6: " + iro);
  }

  public static IAmethystObject constantFallback(AmethystConstantCallSite site,
      ThreadContext context) {
    IAmethystObject value = context.getConstant(site.name());
  
    if (value != null) {
      //if (AmethystInstanceConfig.LOG_INDY_CONSTANTS) LOG.info("constant " + site.name() + " bound directly");
      D("In constantFallback value=" + value); 
      MethodHandle valueHandle = constant(IAmethystObject.class, value);
      valueHandle = dropArguments(valueHandle, 0, ThreadContext.class);

      MethodHandle fallback = insertArguments(
          findStatic(SwitchPointEx.class, "constantFallback",
          methodType(IAmethystObject.class, AmethystConstantCallSite.class, ThreadContext.class)),
          0,
          site);

      SwitchPoint switchPoint = (SwitchPoint)context.runtime.getConstantInvalidator().getData();
      MethodHandle gwt = switchPoint.guardWithTest(valueHandle, fallback);
      site.setTarget(gwt);
    } else {
      D("In constantFallback value=null");
      //value = context.getCurrentScope().getStaticScope().getModule()
      //    .callMethod(context, "const_missing", context.getRuntime().fastNewSymbol(site.name()));
      value = new IAmethystObject("(null)");
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

class IAmethystObject {
  private Object value;
  public IAmethystObject(Object value) { this.value = value; }
  public Object getValue() { return value; }
  public String toString() { return "IAmethystObject{"+value+"}";}
}

class ThreadContext {
  private ConcurrentHashMap<String,IAmethystObject> map = new ConcurrentHashMap<>();
  public IAmethystObject getConstant(String key) {
    return map.get(key);
  }
  public void setConstant(String key, IAmethystObject value) {
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

class AmethystConstantCallSite extends MutableCallSite {
  private String name;
  public AmethystConstantCallSite(MethodType type, String name) {
    super(type);
    setName(name);
  }
  public void setName(String name) { this.name = name; }
  public String name() {
    return this.name;
  }
}
