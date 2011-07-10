package name.yt;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 */
public class MethodHandleTest {
  public static void main(String[] args) {
    final MethodHandleTest receiver = new MethodHandleTest();
    final String methodName = "targetInts";
    int times = 500000;

    for (int idx = 0; idx < 10; ++idx) {
      System.out.println("--");
      benchmark("methodhandle", times, new Callback() {
        MethodHandle method;

        public void before() {
          MethodHandles.Lookup lookup = MethodHandles.lookup();
          MethodType mt = MethodType.methodType(String.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class);
          try {
            method = lookup.findVirtual(receiver.getClass(), methodName, mt);
          } catch (IllegalAccessException | NoSuchMethodException excn) {
            throw new RuntimeException(excn.getMessage(), excn);
          }
        }

        public void call() {
          methodhandle(receiver, method);
        }
      });
      benchmark("reflection ", times, new Callback() {
        Method method;

        public void before() {
          try {
            method = receiver.getClass().getDeclaredMethod(methodName, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class);
          } catch (NoSuchMethodException excn) {
            throw new RuntimeException(excn.getMessage(), excn);
          }
        }

        public void call() {
          reflection(receiver, method);
        }
      });
    }
  }

  interface Callback {
    public void before();

    public void call();
  }

  private static void benchmark(String title, int times, Callback cb) {
    cb.before();
    long start = System.nanoTime();
    for (int idx = 0; idx < times; ++idx) {
      cb.call();
      // cb.before(); for benchmarking method lookup time
    }
    double elapsed = (System.nanoTime() - start) / 1000000.0;
    System.out.println(
        String.format("%s * %d: %3.2f [msec], average: %3.2f [nsec]",
            title, times, elapsed, elapsed / times * 1000.0));
  }

  //private static String methodhandle(Object receiver, MethodHandle method) {
  private static String methodhandle(MethodHandleTest receiver, MethodHandle method) {
    try {
      int[] ints = getRandInts(10);
      return (String) method.invokeExact(receiver, ints[0], ints[1], ints[2], ints[3], ints[4], ints[5], ints[6], ints[7], ints[8], ints[9]); 
    } catch (Throwable t) {
      throw new RuntimeException(t.getMessage(), t);
    }
  }

  private static String reflection(MethodHandleTest receiver, Method method) {
    try {
      int[] ints = getRandInts(10);
      return (String) method.invoke(receiver, ints[0], ints[1], ints[2], ints[3], ints[4], ints[5], ints[6], ints[7], ints[8], ints[9]); 
    } catch (Throwable t) {
      throw new RuntimeException(t.getMessage(), t);
    }
  }

  public String target(String name) {
    return "Hello " + name;
  }
  public String targetInts(int i1, int i2, int i3, int i4, int i5, int i6, int i7, int i8, int i9, int i10) {
    return Integer.toString(i1+i2+i3+i4+i5+i6+i7+i8+i9+i10);
  }

  static java.util.Random rand = new java.util.Random(System.currentTimeMillis());
  static int[] getRandInts(int numInts) {
    int[] ia = new int[numInts];
    for (int i = 0; i < numInts; i++) {
      ia[i] = rand.nextInt(1000000);
    }
    return ia;
  }
}
