package is.uncommon.checks;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;

/**
 * Throws lint errors if methods returning Rx primitives (Observable, Single, etc.) are found without @CheckResult annotation.
 */
public class RxCheckResultAnnotationEnforcer extends Detector implements Detector.UastScanner {

  private static final String ISSUE_ID = RxCheckResultAnnotationEnforcer.class.getSimpleName();
  private static final String ISSUE_BRIEF_DESCRIPTION = "Use @CheckResult";
  private static final String ISSUE_EXPLANATION = "It's easy to forget calling subscribe() on methods that return Rx primitives "
      + "like Observable, Single, etc. Annotate this method with @CheckResult so that AndroidStudio shows a warning when the "
      + "return value is not used.";
  private static final int ISSUE_PRIORITY = 10;   // Highest.
  static final Severity ISSUE_SEVERITY = Severity.ERROR;

  private static final Set<String> RX_PRIMITIVE_CANONICAL_NAMES = new HashSet<>();

  static {
    RX_PRIMITIVE_CANONICAL_NAMES.add("io.reactivex.Observable");
    RX_PRIMITIVE_CANONICAL_NAMES.add("io.reactivex.Single");
    RX_PRIMITIVE_CANONICAL_NAMES.add("io.reactivex.Completable");
    RX_PRIMITIVE_CANONICAL_NAMES.add("io.reactivex.Maybe");
    RX_PRIMITIVE_CANONICAL_NAMES.add("io.reactivex.Flowable");
  }

  static final Issue ISSUE = Issue.create(
      ISSUE_ID,
      ISSUE_BRIEF_DESCRIPTION,
      ISSUE_EXPLANATION,
      Category.CORRECTNESS,
      ISSUE_PRIORITY,
      ISSUE_SEVERITY,
      new Implementation(RxCheckResultAnnotationEnforcer.class, Scope.JAVA_FILE_SCOPE)
  );

  @Override
  public EnumSet<Scope> getApplicableFiles() {
    return Scope.JAVA_FILE_SCOPE;
  }

  @Override
  public List<Class<? extends UElement>> getApplicableUastTypes() {
    return Collections.singletonList(UMethod.class);
  }

  @Override
  public UElementHandler createUastHandler(JavaContext context) {
    return new UElementHandler() {
      @Override
      public void visitMethod(UMethod method) {
        if (method.getReturnType() == null || PsiType.VOID.equals(method.getReturnType())) {
          // Constructor or void return type.
          return;
        }

        boolean isRxReturnType = isReturnValueRxPrimitive(method.getReturnType());
        if (!isRxReturnType) {
          return;
        }

        boolean isCheckReturnAnnotationMissing = method.findAnnotation("android.support.annotation.CheckResult") == null;
        if (isCheckReturnAnnotationMissing) {
          context.report(ISSUE, method, context.getLocation(method), "Should annotate return value with @CheckResult");
        }
      }

      private boolean isReturnValueRxPrimitive(PsiType returnType) {
        String returnTypeName = removeTypeFromClassName(returnType.getCanonicalText());
        return RX_PRIMITIVE_CANONICAL_NAMES.contains(returnTypeName) || hasRxSuperType(returnType);
      }

      /**
       * Check if a PsiType has an Rx primitive as its super class, by walking up the class hierarchy.
       */
      private boolean hasRxSuperType(PsiType psiType) {
        // PsiType#getSuperTypes() returns the super class and any interfaces this PsiType implements.
        // We're assuming that the super class will always be present in the 0th index.
        PsiType[] superTypes = psiType.getSuperTypes();

        while (superTypes.length > 0) {
          final PsiType nextSuperClassType = superTypes[0];

          if (RX_PRIMITIVE_CANONICAL_NAMES.contains(removeTypeFromClassName(nextSuperClassType.getCanonicalText()))) {
            return true;
          }
          superTypes = nextSuperClassType.getSuperTypes();
        }

        return false;
      }
    };
  }

  /**
   * Convert {@code "io.reactivex.Observable<Object>"} to {@code "io.reactivex.Observable"}.
   */
  static String removeTypeFromClassName(String className) {
    int typeStartIndex = className.indexOf("<");
    return typeStartIndex != -1
        ? className.substring(0, typeStartIndex)
        : className;
  }
}
