package com.etl.simupaylog.plugin;

import com.android.build.api.instrumentation.AsmClassVisitorFactory;
import com.android.build.api.instrumentation.ClassContext;
import com.android.build.api.instrumentation.ClassData;
import com.android.build.api.instrumentation.InstrumentationParameters;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Set;

/**
 * ASM class visitor factory that rewrites {@code android.util.Log} method calls
 * to {@code com.etl.simupaylog.Log} in the project's bytecode.
 */
public abstract class LogRedirectClassVisitorFactory
        implements AsmClassVisitorFactory<InstrumentationParameters.None> {

    private static final String ANDROID_LOG_INTERNAL = "android/util/Log";
    private static final String SIMUPAY_LOG_INTERNAL = "com/etl/simupaylog/Log";

    /** Methods present in com.etl.simupaylog.Log that we can safely redirect. */
    private static final Set<String> REDIRECTABLE_METHODS = Set.of(
            "v", "d", "i", "w", "e",
            "getStackTraceString",
            "isLoggable"
    );

    @Override
    public ClassVisitor createClassVisitor(ClassContext classContext, ClassVisitor nextClassVisitor) {
        return new RedirectClassVisitor(Opcodes.ASM9, nextClassVisitor);
    }

    @Override
    public boolean isInstrumentable(ClassData classData) {
        // Never instrument our own library — it intentionally delegates to android.util.Log
        return !classData.getClassName().startsWith("com.etl.simupaylog.");
    }

    /**
     * ClassVisitor that delegates to {@link RedirectMethodVisitor} for every method body.
     */
    private static class RedirectClassVisitor extends ClassVisitor {

        RedirectClassVisitor(int api, ClassVisitor cv) {
            super(api, cv);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (mv != null) {
                mv = new RedirectMethodVisitor(api, mv);
            }
            return mv;
        }
    }

    /**
     * MethodVisitor that intercepts {@code INVOKESTATIC android/util/Log.*} instructions
     * and rewrites the owner to {@code com/etl/simupaylog/Log}.
     */
    private static class RedirectMethodVisitor extends MethodVisitor {

        RedirectMethodVisitor(int api, MethodVisitor mv) {
            super(api, mv);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name,
                                    String descriptor, boolean isInterface) {
            if (opcode == Opcodes.INVOKESTATIC
                    && ANDROID_LOG_INTERNAL.equals(owner)
                    && REDIRECTABLE_METHODS.contains(name)) {
                // Redirect to SimuPay Log
                super.visitMethodInsn(opcode, SIMUPAY_LOG_INTERNAL, name, descriptor, isInterface);
            } else {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        }
    }
}
