package net.bytebuddy.dynamic.scaffold.inline;

import net.bytebuddy.dynamic.scaffold.BridgeMethodResolver;
import net.bytebuddy.instrumentation.Instrumentation;
import net.bytebuddy.instrumentation.method.MethodDescription;
import net.bytebuddy.instrumentation.method.MethodLookupEngine;
import net.bytebuddy.instrumentation.method.bytecode.stack.StackManipulation;
import net.bytebuddy.instrumentation.method.bytecode.stack.constant.NullConstant;
import net.bytebuddy.instrumentation.method.bytecode.stack.member.MethodInvocation;
import net.bytebuddy.instrumentation.method.matcher.MethodMatcher;
import net.bytebuddy.instrumentation.type.TypeDescription;
import org.objectweb.asm.MethodVisitor;

public class RebaseInstrumentationTarget extends Instrumentation.Target.AbstractBase {

    protected final MethodFlatteningResolver methodFlatteningResolver;

    protected RebaseInstrumentationTarget(MethodLookupEngine.Finding finding,
                                          BridgeMethodResolver.Factory bridgeMethodResolverFactory,
                                          MethodFlatteningResolver methodFlatteningResolver) {
        super(finding, bridgeMethodResolverFactory);
        this.methodFlatteningResolver = methodFlatteningResolver;
    }

    @Override
    protected Instrumentation.SpecialMethodInvocation invokeSuper(MethodDescription methodDescription) {
        return methodDescription.getDeclaringType().equals(typeDescription)
                ? invokeSuper(methodFlatteningResolver.resolve(methodDescription))
                : Instrumentation.SpecialMethodInvocation.Simple.of(methodDescription, typeDescription.getSupertype());
    }

    /**
     * Defines a special method invocation on type level. This means that invoke super instructions are not explicitly
     * dispatched on the super type but on the instrumented type. This allows to call methods non-virtually even though
     * they are not defined on the super type. Redefined constructors are not renamed by are added an additional
     * parameter of a type which is only used for this purpose. Additionally, a {@code null} value is loaded onto the
     * stack when the special method invocation is applied in order to fill the operand stack with an additional caller
     * argument. Non-constructor methods are renamed.
     *
     * @param resolution A proxied super method invocation on the instrumented type.
     * @return A special method invocation on this proxied super method.
     */
    private Instrumentation.SpecialMethodInvocation invokeSuper(MethodFlatteningResolver.Resolution resolution) {
        return resolution.isRedefined() && resolution.getResolvedMethod().isConstructor()
                ? new RedefinedConstructorInvocation(resolution.getResolvedMethod(), typeDescription)
                : Instrumentation.SpecialMethodInvocation.Simple.of(resolution.getResolvedMethod(), typeDescription);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || !(other == null || getClass() != other.getClass())
                && super.equals(other)
                && methodFlatteningResolver.equals(((RebaseInstrumentationTarget) other).methodFlatteningResolver);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + methodFlatteningResolver.hashCode();
    }

    @Override
    public String toString() {
        return "RebaseInstrumentationTarget{" +
                "typeDescription=" + typeDescription +
                ", defaultMethods=" + defaultMethods +
                ", bridgeMethodResolver=" + bridgeMethodResolver +
                ", methodRedefinitionResolver=" + methodFlatteningResolver +
                '}';
    }

    protected static class RedefinedConstructorInvocation implements Instrumentation.SpecialMethodInvocation {

        private final MethodDescription methodDescription;

        private final TypeDescription typeDescription;

        private final StackManipulation stackManipulation;

        public RedefinedConstructorInvocation(MethodDescription methodDescription, TypeDescription typeDescription) {
            this.methodDescription = methodDescription;
            this.typeDescription = typeDescription;
            stackManipulation = new Compound(NullConstant.INSTANCE, MethodInvocation.invoke(methodDescription));
        }

        @Override
        public MethodDescription getMethodDescription() {
            return methodDescription;
        }

        @Override
        public TypeDescription getTypeDescription() {
            return typeDescription;
        }

        @Override
        public boolean isValid() {
            return stackManipulation.isValid();
        }

        @Override
        public Size apply(MethodVisitor methodVisitor, Instrumentation.Context instrumentationContext) {
            return stackManipulation.apply(methodVisitor, instrumentationContext);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            Instrumentation.SpecialMethodInvocation specialMethodInvocation = (Instrumentation.SpecialMethodInvocation) other;
            return isValid() == specialMethodInvocation.isValid()
                    && typeDescription.equals(specialMethodInvocation.getTypeDescription())
                    && methodDescription.getInternalName().equals(specialMethodInvocation.getMethodDescription().getInternalName())
                    && methodDescription.getParameterTypes().equals(specialMethodInvocation.getMethodDescription().getParameterTypes())
                    && methodDescription.getReturnType().equals(specialMethodInvocation.getMethodDescription().getReturnType());
        }

        @Override
        public int hashCode() {
            int result = methodDescription.getInternalName().hashCode();
            result = 31 * result + methodDescription.getParameterTypes().hashCode();
            result = 31 * result + methodDescription.getReturnType().hashCode();
            result = 31 * result + typeDescription.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "RebaseInstrumentationTarget.RedefinedConstructorInvocation{" +
                    "typeDescription=" + typeDescription +
                    ", methodDescription=" + methodDescription +
                    '}';
        }
    }

    public static class Factory implements Instrumentation.Target.Factory {

        private final BridgeMethodResolver.Factory bridgeMethodResolverFactory;

        private final MethodMatcher ignoredMethods;

        private final TypeDescription placeholderType;

        public Factory(BridgeMethodResolver.Factory bridgeMethodResolverFactory,
                       MethodMatcher ignoredMethods,
                       TypeDescription placeholderType) {
            this.bridgeMethodResolverFactory = bridgeMethodResolverFactory;
            this.ignoredMethods = ignoredMethods;
            this.placeholderType = placeholderType;
        }

        @Override
        public Instrumentation.Target make(MethodLookupEngine.Finding finding) {
            return new RebaseInstrumentationTarget(finding,
                    bridgeMethodResolverFactory,
                    new MethodFlatteningResolver.Default(finding.getTypeDescription().getDeclaredMethods(),
                            ignoredMethods,
                            placeholderType));
        }
    }
}