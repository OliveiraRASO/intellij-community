import java.lang.invoke.*;

public class Main {
  void foo() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    lookup.findVirtual(Types.class, "strMethod", MethodType.methodType(String.class, int.class, int.class));
  }
}