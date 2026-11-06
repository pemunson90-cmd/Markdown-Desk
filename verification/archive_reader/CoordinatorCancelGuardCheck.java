package com.example.markdowndesk;
import java.lang.reflect.Field;

/** No Android device required: verify stale Cancel cannot change an idle,
 * completed, or already-cancelling job into an immortal running state.
 */
public final class CoordinatorCancelGuardCheck {
  private static void check(boolean condition,String description) {
    if(!condition) throw new AssertionError(description);
  }
  private static void field(Object target,String name,Object value) throws Exception {
    Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);
  }
  public static void main(String[] args) throws Exception {
    sun.misc.Unsafe unsafe;
    Field theUnsafe=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    theUnsafe.setAccessible(true);unsafe=(sun.misc.Unsafe)theUnsafe.get(null);
    ArchiveImportCoordinator coordinator=(ArchiveImportCoordinator)
        unsafe.allocateInstance(ArchiveImportCoordinator.class);
    // An inactive coordinator has no saved preferences, worker or app context.
    // Any mistaken transition into update() will fail this test immediately.
    coordinator.cancel();
    field(coordinator,"busy",true);
    field(coordinator,"snapshot",new ArchiveImportCoordinator.Snapshot(
          "complete","finished",null,1L,1L,1L,1L,1,1));
    coordinator.cancel();
    field(coordinator,"snapshot",new ArchiveImportCoordinator.Snapshot(
          "failed","failed",null,1L,1L,1L,1L,1,1));
    coordinator.cancel();
    check(!coordinator.snapshot().running(),"stale cancel must not reactivate terminal state");
    System.out.println("PASS: idle and terminal-state Cancel are inert");
  }
}