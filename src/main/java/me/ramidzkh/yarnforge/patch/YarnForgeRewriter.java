package me.ramidzkh.yarnforge.patch;

import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.mercury.RewriteContext;
import org.cadixdev.mercury.SourceRewriter;

public class YarnForgeRewriter implements SourceRewriter {

    private final MappingSet mappings;

    public YarnForgeRewriter(MappingSet mappings) {
        this.mappings = mappings;
    }

    @Override
    public int getFlags() {
        return FLAG_RESOLVE_BINDINGS;
    }

    @Override
    public void rewrite(RewriteContext context) {
        context.getCompilationUnit().accept(new OnlyInVisitor(context));
    }
}
